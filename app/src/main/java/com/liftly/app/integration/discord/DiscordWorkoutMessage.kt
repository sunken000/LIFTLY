package com.liftly.app.integration.discord

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.SessionEntity
import com.liftly.app.data.SessionSetEntity
import com.liftly.app.data.UserProfileEntity
import com.liftly.app.domain.WorkoutCalorieEstimator
import java.time.Instant
import java.util.Locale
import kotlin.math.absoluteValue

data class DiscordWorkoutMessage(
    val json: String,
    val completedSets: Int,
    val volumeKg: Double,
    val estimatedKilocalories: Int?,
)

/** Produces a Discord embed while respecting Discord's field and embed-size limits. */
object DiscordWorkoutMessageFactory {
    private const val DISCORD_EMBED_TEXT_LIMIT = 6_000
    private const val MAX_EXERCISE_FIELDS = 20
    private const val MAX_FIELD_NAME = 256
    private const val MAX_FIELD_VALUE = 900
    private val portuguese = Locale.forLanguageTag("pt-BR")

    fun create(
        session: SessionEntity,
        allSets: List<SessionSetEntity>,
        exercises: List<ExerciseEntity>,
        profile: UserProfileEntity?,
    ): DiscordWorkoutMessage? {
        val finishedAt = session.finishedAt ?: return null
        if (session.isTestMode) return null

        val sets = allSets
            .asSequence()
            .filter { it.sessionId == session.id && it.completed }
            .sortedWith(compareBy<SessionSetEntity> { it.exerciseOrder }.thenBy { it.setNumber })
            .toList()
        if (sets.isEmpty()) return null

        val volume = sets.sumOf { set ->
            if (set.isRepetitionBased()) set.loadKg.coerceAtLeast(0.0) * set.reps.coerceAtLeast(0) else 0.0
        }
        val calorieEstimate = WorkoutCalorieEstimator.estimate(
            session = session,
            sets = sets,
            exercises = exercises,
            bodyWeightKg = profile?.currentWeightKg,
        )
        val durationMillis = (finishedAt - session.startedAt).coerceAtLeast(0L)
        val nickname = profile?.nickname?.trim().orEmpty()
        val description = if (nickname.isBlank()) {
            "Mais um treino registrado pelo Liftly."
        } else {
            "Treino de ${nickname.truncated(120)} registrado pelo Liftly."
        }
        val title = "Treino concluído • ${session.workoutName.ifBlank { "Treino" }.truncated(220)}"
        val footer = if (calorieEstimate == null) {
            "Calorias indisponíveis: informe um peso válido no perfil."
        } else {
            "Calorias são uma estimativa populacional por MET, peso e duração."
        }

        val fields = mutableListOf(
            EmbedField("Duração", formatDuration(durationMillis), true),
            EmbedField("Séries concluídas", sets.size.toString(), true),
            EmbedField("Volume", "${formatDecimal(volume)} kg", true),
            EmbedField("Calorias estimadas", calorieEstimate?.let { "${it.kilocalories} kcal" } ?: "Não disponível", true),
        )

        var usedCharacters = title.length + description.length + footer.length +
            fields.sumOf { it.name.length + it.value.length }
        val groups = sets.groupBy { it.exerciseId.ifBlank { it.exerciseName } }.values.toList()
        var exportedGroups = 0
        for (group in groups) {
            if (exportedGroups >= MAX_EXERCISE_FIELDS || fields.size >= 25) break
            val name = group.first().exerciseName.ifBlank { "Exercício" }.truncated(MAX_FIELD_NAME)
            // Reserve space for the footer suffix when some exercises have to be omitted.
            val remaining = DISCORD_EMBED_TEXT_LIMIT - usedCharacters - name.length - 160
            if (remaining < 80) break
            val value = group.joinToString("\n") { it.toDiscordLine() }
                .truncated(minOf(MAX_FIELD_VALUE, remaining))
            fields += EmbedField(name, value, false)
            usedCharacters += name.length + value.length
            exportedGroups++
        }

        val omitted = groups.size - exportedGroups
        val safeFooter = if (omitted > 0) "$footer • $omitted exercício(s) omitido(s) por limite do Discord." else footer
        val json = buildString {
            append('{')
            append("\"username\":\"Liftly\",")
            append("\"allowed_mentions\":{\"parse\":[]},")
            append("\"embeds\":[{")
            append("\"title\":").appendJsonString(title).append(',')
            append("\"description\":").appendJsonString(description).append(',')
            append("\"color\":10027263,")
            append("\"timestamp\":").appendJsonString(Instant.ofEpochMilli(finishedAt).toString()).append(',')
            append("\"fields\":[")
            fields.forEachIndexed { index, field ->
                if (index > 0) append(',')
                append('{')
                append("\"name\":").appendJsonString(field.name).append(',')
                append("\"value\":").appendJsonString(field.value).append(',')
                append("\"inline\":").append(field.inline)
                append('}')
            }
            append("],\"footer\":{\"text\":").appendJsonString(safeFooter.truncated(2_048)).append("}}]")
            append('}')
        }
        return DiscordWorkoutMessage(json, sets.size, volume, calorieEstimate?.kilocalories)
    }

    private fun SessionSetEntity.toDiscordLine(): String {
        val prefix = "S$setNumber: "
        val metric = when {
            trackingMode.contains("tempo", ignoreCase = true) -> {
                val seconds = durationSeconds.takeIf { it > 0 } ?: reps.coerceAtLeast(0)
                prefix + formatSeconds(seconds) + plannedValueChange(seconds, "s")
            }

            trackingMode.contains("dist", ignoreCase = true) -> {
                val meters = distanceMeters.takeIf { it > 0.0 } ?: reps.toDouble().coerceAtLeast(0.0)
                prefix + "${formatDecimal(meters)} m" + plannedDistanceChange(meters)
            }

            else -> {
                val load = if (loadKg > 0.0) " × ${formatDecimal(loadKg)} kg" else ""
                prefix + "$reps reps$load" + plannedRepetitionChange()
            }
        }
        val effort = buildList {
            rir?.let { add("RIR $it") }
            if (painLevel > 0) add("dor $painLevel/10")
        }
        return if (effort.isEmpty()) metric else "$metric · ${effort.joinToString(" · ")}"
    }

    private fun SessionSetEntity.plannedRepetitionChange(): String {
        val plannedRepsValue = plannedReps ?: return ""
        val plannedLoadValue = plannedLoadKg
        val repsDelta = reps - plannedRepsValue
        val loadDelta = plannedLoadValue?.let { loadKg - it }
        if (repsDelta == 0 && (loadDelta == null || loadDelta.absoluteValue < 0.005)) return ""

        val planLoad = plannedLoadValue?.let { " × ${formatDecimal(it)} kg" }.orEmpty()
        val deltas = buildList {
            if (repsDelta != 0) add("${signed(repsDelta)} reps")
            if (loadDelta != null && loadDelta.absoluteValue >= 0.005) add("${signed(loadDelta)} kg")
        }
        return " · plano $plannedRepsValue reps$planLoad · Δ ${deltas.joinToString(" / ")}"
    }

    private fun SessionSetEntity.plannedValueChange(actual: Int, unit: String): String {
        val planned = plannedReps ?: return ""
        val delta = actual - planned
        return if (delta == 0) "" else " · plano $planned $unit · Δ ${signed(delta)} $unit"
    }

    private fun SessionSetEntity.plannedDistanceChange(actual: Double): String {
        val planned = plannedReps?.toDouble() ?: return ""
        val delta = actual - planned
        return if (delta.absoluteValue < 0.005) "" else
            " · plano ${formatDecimal(planned)} m · Δ ${signed(delta)} m"
    }

    private fun SessionSetEntity.isRepetitionBased(): Boolean =
        !trackingMode.contains("tempo", ignoreCase = true) &&
            !trackingMode.contains("dist", ignoreCase = true)

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1_000
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) "%dh %02dmin %02ds".format(portuguese, hours, minutes, remainingSeconds)
        else "%dmin %02ds".format(portuguese, minutes, remainingSeconds)
    }

    private fun formatSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val remainder = seconds % 60
        return if (minutes > 0) "${minutes}min ${remainder}s" else "${remainder}s"
    }

    private fun formatDecimal(value: Double): String =
        if (value.absoluteValue < 0.005) "0" else String.format(portuguese, "%.1f", value)

    private fun signed(value: Double): String {
        val formatted = formatDecimal(value.absoluteValue)
        return if (value >= 0.0) "+$formatted" else "-$formatted"
    }

    private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

    private fun String.truncated(maxLength: Int): String = when {
        length <= maxLength -> this
        maxLength <= 1 -> take(maxLength)
        else -> take(maxLength - 1).trimEnd() + "…"
    }

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(Locale.ROOT, character.code)) else append(character)
            }
        }
        return append('"')
    }

    private data class EmbedField(val name: String, val value: String, val inline: Boolean)
}
