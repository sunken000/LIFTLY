package com.liftly.app.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Portable, versioned representation of one workout plan.
 *
 * Session history, calendar entries and personal information are deliberately absent. Exercise
 * image URIs are also omitted because a content URI is private to the device that selected it.
 */
data class SharedWorkoutPackage(
    val type: String = WorkoutShareCodec.TYPE,
    val schemaVersion: Int = WorkoutShareCodec.SCHEMA_VERSION,
    val exportedAt: Long,
    val workout: SharedWorkout,
    val exercises: List<SharedExercise>,
    val items: List<SharedWorkoutItem>,
)

data class SharedWorkout(
    val name: String,
    val description: String,
    val color: Long,
    val icon: String,
    val weekDays: String,
)

data class SharedExercise(
    val referenceId: String,
    val name: String,
    val muscleGroup: String,
    val secondaryMuscles: String,
    val equipment: String,
    val difficulty: String,
    val movementType: String,
    val category: String,
    val instructions: String,
    val cautions: String,
    val trackingUnit: String,
    val isCustom: Boolean,
)

data class SharedWorkoutItem(
    val exerciseReferenceId: String,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetLoadKg: Double,
    val restSeconds: Int,
    val notes: String,
    val setType: String,
    val trackingMode: String,
)

object WorkoutShareCodec {
    const val TYPE = "com.liftly.workout"
    const val SCHEMA_VERSION = 1
    const val FILE_EXTENSION = "liftlyworkout"
    const val MIME_TYPE = "application/vnd.liftly.workout+json"
    const val QR_PREFIX = "LIFTLY-WORKOUT-1:"
    private const val MAX_PAYLOAD_CHARACTERS = 1_000_000
    private val gson = Gson()

    fun encode(value: SharedWorkoutPackage): String {
        validate(value)
        return gson.toJson(value)
    }

    fun decode(raw: String): SharedWorkoutPackage {
        require(raw.length <= MAX_PAYLOAD_CHARACTERS) { "O arquivo de treino é grande demais." }
        val root = try {
            JsonParser.parseString(raw)
        } catch (_: JsonParseException) {
            throw IllegalArgumentException("Arquivo de treino inválido.")
        }
        require(root.isJsonObject) { "Arquivo de treino inválido." }
        val value: SharedWorkoutPackage = try {
            gson.fromJson(root, SharedWorkoutPackage::class.java)
                ?: throw IllegalArgumentException("Arquivo de treino inválido.")
        } catch (_: RuntimeException) {
            throw IllegalArgumentException("Arquivo de treino inválido.")
        }
        validate(value)
        return value
    }

    /** Compact transport used inside QR codes; the canonical file format remains readable JSON. */
    fun encodeForQr(jsonPayload: String): String {
        decode(jsonPayload)
        val compressed = ByteArrayOutputStream().use { bytes ->
            GZIPOutputStream(bytes).use { gzip -> gzip.write(jsonPayload.toByteArray(Charsets.UTF_8)) }
            bytes.toByteArray()
        }
        return QR_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decodeFromQr(qrPayload: String): String {
        require(qrPayload.startsWith(QR_PREFIX)) { "Este QR não contém um treino do Liftly." }
        val compressed = runCatching {
            Base64.getUrlDecoder().decode(qrPayload.removePrefix(QR_PREFIX))
        }.getOrElse { throw IllegalArgumentException("QR de treino inválido.") }
        require(compressed.size <= MAX_PAYLOAD_CHARACTERS) { "QR de treino grande demais." }
        val json = runCatching {
            GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { reader ->
                readLimitedText(reader)
            }
        }.getOrElse { throw IllegalArgumentException(it.message ?: "QR de treino inválido.") }
        decode(json)
        return json
    }

    fun fromEntities(
        workout: WorkoutEntity,
        items: List<WorkoutExerciseEntity>,
        exercises: Map<String, ExerciseEntity>,
        exportedAt: Long = System.currentTimeMillis(),
    ): SharedWorkoutPackage {
        val ordered = items.sortedWith(compareBy<WorkoutExerciseEntity> { it.orderIndex }.thenBy { it.id })
        val referencedExercises = ordered.map { item ->
            requireNotNull(exercises[item.exerciseId]) { "Exercício indisponível: ${item.exerciseId}." }
        }.distinctBy { it.id }
        return SharedWorkoutPackage(
            exportedAt = exportedAt,
            workout = SharedWorkout(
                name = workout.name,
                description = workout.description,
                color = workout.color,
                icon = workout.icon,
                weekDays = workout.weekDays,
            ),
            exercises = referencedExercises.map { exercise ->
                SharedExercise(
                    referenceId = exercise.id,
                    name = exercise.name,
                    muscleGroup = exercise.muscleGroup,
                    secondaryMuscles = exercise.secondaryMuscles,
                    equipment = exercise.equipment,
                    difficulty = exercise.difficulty,
                    movementType = exercise.movementType,
                    category = exercise.category,
                    instructions = exercise.instructions,
                    cautions = exercise.cautions,
                    trackingUnit = exercise.trackingUnit,
                    isCustom = exercise.isCustom,
                )
            },
            items = ordered.mapIndexed { index, item ->
                SharedWorkoutItem(
                    exerciseReferenceId = item.exerciseId,
                    orderIndex = index,
                    sets = item.sets,
                    repMin = item.repMin,
                    repMax = item.repMax,
                    targetLoadKg = item.targetLoadKg,
                    restSeconds = item.restSeconds,
                    notes = item.notes,
                    setType = item.setType,
                    trackingMode = item.trackingMode,
                )
            },
        ).also(::validate)
    }

    private fun validate(value: SharedWorkoutPackage) {
        require(value.type == TYPE) { "Este arquivo não é um treino do Liftly." }
        require(value.schemaVersion == SCHEMA_VERSION) { "Versão de treino compartilhado incompatível." }
        require(value.exportedAt > 0L) { "Data de exportação inválida." }
        require(value.workout.name.trim().length in 1..80) { "Nome do treino inválido." }
        require(value.workout.description.length <= 600) { "Descrição do treino é muito longa." }
        require(value.workout.icon.length <= 40) { "Ícone do treino inválido." }
        require(validWeekDays(value.workout.weekDays)) { "Dias da semana inválidos." }
        require(value.items.size in 1..100) { "O treino deve conter de 1 a 100 exercícios." }
        require(value.exercises.size in 1..100) { "Lista de exercícios inválida." }

        val references = mutableSetOf<String>()
        value.exercises.forEach { exercise ->
            require(exercise.referenceId.isNotBlank() && references.add(exercise.referenceId)) {
                "Referência de exercício duplicada ou vazia."
            }
            require(exercise.name.trim().length in 1..80) { "Nome de exercício inválido." }
            require(exercise.muscleGroup.length <= 80 && exercise.secondaryMuscles.length <= 240) {
                "Grupos musculares inválidos."
            }
            require(exercise.equipment.length <= 120 && exercise.difficulty.length <= 40) {
                "Equipamento ou dificuldade inválidos."
            }
            require(exercise.movementType.length <= 80 && exercise.category.length <= 80) {
                "Classificação do exercício inválida."
            }
            require(exercise.instructions.length <= 4_000 && exercise.cautions.length <= 2_000) {
                "Instruções do exercício são muito longas."
            }
            require(exercise.trackingUnit.length in 1..40) { "Unidade de acompanhamento inválida." }
        }

        val expectedOrder = value.items.indices.toList()
        require(value.items.sortedBy { it.orderIndex }.map { it.orderIndex } == expectedOrder) {
            "A ordem dos exercícios está corrompida."
        }
        value.items.forEach { item ->
            require(item.exerciseReferenceId in references) { "Exercício referenciado não encontrado." }
            require(item.sets in 1..20) { "Número de séries inválido." }
            require(item.repMin in 0..10_000 && item.repMax in item.repMin..10_000) {
                "Faixa de repetições inválida."
            }
            require(item.targetLoadKg in 0.0..2_000.0 && item.targetLoadKg.isFinite()) { "Carga inválida." }
            require(item.restSeconds in 0..3_600) { "Descanso inválido." }
            require(item.notes.length <= 1_000 && item.setType.length <= 40 && item.trackingMode.length <= 40) {
                "Configuração de exercício inválida."
            }
        }
    }

    private fun validWeekDays(raw: String): Boolean = raw.isBlank() || raw.split(',').all { token ->
        token.trim().toIntOrNull() in 1..7
    }

    private fun readLimitedText(reader: java.io.Reader): String {
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(output.length + count <= MAX_PAYLOAD_CHARACTERS) { "QR de treino grande demais." }
            output.append(buffer, 0, count)
        }
        return output.toString()
    }
}
