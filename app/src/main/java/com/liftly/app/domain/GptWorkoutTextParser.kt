package com.liftly.app.domain

import java.text.Normalizer
import java.time.DayOfWeek

/** Resultado editável que a interface deve mostrar antes de salvar qualquer ficha. */
data class WorkoutTextParseResult(
    val workouts: List<ParsedWorkout>,
    val warnings: List<WorkoutTextWarning> = emptyList(),
    val ignoredLines: List<String> = emptyList(),
    val sourceLineCount: Int = 0,
) {
    val exerciseCount: Int get() = workouts.sumOf { it.exercises.size }
    val canImport: Boolean get() = workouts.any { it.exercises.isNotEmpty() }
}

data class ParsedWorkout(
    val name: String,
    val description: String = "",
    val exercises: List<ParsedWorkoutExercise>,
    val weekDays: Set<DayOfWeek> = emptySet(),
)

data class ParsedWorkoutExercise(
    val name: String,
    val sets: Int? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val loadKg: Double? = null,
    val restSeconds: Int? = null,
    val rir: Int? = null,
    val setType: ParsedSetType? = null,
    val notes: String = "",
    val sourceLine: Int,
)

enum class ParsedSetType {
    NORMAL,
    WARM_UP,
    DROP_SET,
    SUPER_SET,
    FAILURE,
    UNKNOWN,
}

enum class WorkoutTextWarningCode {
    EMPTY_INPUT,
    NO_EXERCISES,
    INFERRED_WORKOUT_NAME,
    INCOMPLETE_EXERCISE,
    INVALID_SETS,
    INVALID_REPS,
    INVALID_LOAD,
    INVALID_REST,
    INVALID_RIR,
    ORPHAN_METADATA,
    AMBIGUOUS_LINE,
}

data class WorkoutTextWarning(
    val code: WorkoutTextWarningCode,
    val message: String,
    val lineNumber: Int? = null,
)

/**
 * Interpreta fichas em texto/Markdown sem rede e sem completar valores ausentes.
 *
 * O parser é propositalmente conservador: números fora de limites plausíveis são descartados e
 * viram alertas. A tela de prévia é responsável pela revisão humana antes da persistência.
 */
object GptWorkoutTextParser {
    fun parse(raw: String): WorkoutTextParseResult {
        val sourceLines = raw.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (raw.isBlank()) {
            return WorkoutTextParseResult(
                workouts = emptyList(),
                warnings = listOf(
                    WorkoutTextWarning(WorkoutTextWarningCode.EMPTY_INPUT, "Cole uma ficha de treino para continuar."),
                ),
                sourceLineCount = sourceLines.size,
            )
        }

        val warnings = mutableListOf<WorkoutTextWarning>()
        val ignored = mutableListOf<String>()
        val workouts = mutableListOf<DraftWorkout>()
        var currentWorkout: DraftWorkout? = null
        var currentExercise: DraftExercise? = null
        var tableHeader: TableHeader? = null

        fun ensureWorkout(lineNumber: Int): DraftWorkout {
            currentWorkout?.let { return it }
            return DraftWorkout("Treino importado", emptySet()).also {
                workouts += it
                currentWorkout = it
                warnings += WorkoutTextWarning(
                    WorkoutTextWarningCode.INFERRED_WORKOUT_NAME,
                    "O texto não informou o nome do treino; revise o nome sugerido.",
                    lineNumber,
                )
            }
        }

        sourceLines.forEachIndexed { zeroIndex, originalLine ->
            val lineNumber = zeroIndex + 1
            val trimmed = originalLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("```")) return@forEachIndexed

            if (trimmed.contains('|')) {
                val cells = markdownCells(trimmed)
                val detectedHeader = detectTableHeader(cells)
                if (detectedHeader != null) {
                    tableHeader = detectedHeader
                    currentExercise = null
                    return@forEachIndexed
                }
                if (isMarkdownSeparator(cells)) return@forEachIndexed
                tableHeader?.let { header ->
                    val parsed = parseTableRow(cells, header, lineNumber, warnings)
                    if (parsed != null) {
                        val workout = ensureWorkout(lineNumber)
                        val draft = parsed.toDraft()
                        workout.exercises += draft
                        currentExercise = draft
                    } else {
                        ignored += trimmed
                    }
                    return@forEachIndexed
                }
            } else {
                tableHeader = null
            }

            val cleaned = cleanMarkdownLine(trimmed)
            if (cleaned.isBlank() || isDecorativeSeparator(cleaned)) return@forEachIndexed

            val daysOnLine = extractWeekDays(cleaned)
            if (currentWorkout != null && daysOnLine.isNotEmpty() && containsOnlyWeekDays(cleaned)) {
                currentWorkout!!.weekDays = currentWorkout!!.weekDays + daysOnLine
                currentExercise = null
                return@forEachIndexed
            }

            if (isWorkoutHeader(trimmed, cleaned)) {
                val name = cleaned.trim().trimEnd(':').ifBlank { "Treino importado" }
                currentWorkout = DraftWorkout(name, extractWeekDays(cleaned)).also(workouts::add)
                currentExercise = null
                return@forEachIndexed
            }

            if (isGenericSectionHeading(cleaned)) {
                currentExercise = null
                return@forEachIndexed
            }

            if (isMetadataLine(cleaned)) {
                val target = currentExercise
                if (target == null) {
                    warnings += WorkoutTextWarning(
                        WorkoutTextWarningCode.ORPHAN_METADATA,
                        "Parâmetro encontrado antes de um exercício e ignorado: $cleaned",
                        lineNumber,
                    )
                    ignored += trimmed
                } else {
                    applyMetadata(target, cleaned, lineNumber, warnings)
                }
                return@forEachIndexed
            }

            val parsed = parseExerciseLine(trimmed, cleaned, lineNumber, warnings)
            if (parsed != null) {
                val workout = ensureWorkout(lineNumber)
                val draft = parsed.toDraft()
                workout.exercises += draft
                currentExercise = draft
            } else if (!isNarrativeLine(cleaned)) {
                ignored += trimmed
                warnings += WorkoutTextWarning(
                    WorkoutTextWarningCode.AMBIGUOUS_LINE,
                    "Linha não reconhecida; confira a prévia: $cleaned",
                    lineNumber,
                )
            }
        }

        workouts.forEach { workout ->
            workout.exercises.forEach { exercise ->
                if (exercise.sets == null || exercise.repMin == null) {
                    warnings += WorkoutTextWarning(
                        WorkoutTextWarningCode.INCOMPLETE_EXERCISE,
                        "${exercise.name}: séries ou repetições não foram informadas.",
                        exercise.sourceLine,
                    )
                }
            }
        }

        if (workouts.none { it.exercises.isNotEmpty() }) {
            warnings += WorkoutTextWarning(
                WorkoutTextWarningCode.NO_EXERCISES,
                "Nenhum exercício pôde ser reconhecido. Use, por exemplo: Supino reto — 3 x 8–12.",
            )
        }

        return WorkoutTextParseResult(
            workouts = workouts.filter { it.exercises.isNotEmpty() }.map(DraftWorkout::freeze),
            warnings = warnings.distinct(),
            ignoredLines = ignored.distinct(),
            sourceLineCount = sourceLines.size,
        )
    }

    private fun parseExerciseLine(
        original: String,
        cleaned: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ): ParsedWorkoutExercise? {
        val markerCandidate = hasListMarker(original) || hasStrongMarkdown(original)
        val prescriptionFirst = PRESCRIPTION_FIRST.find(cleaned)
        val metadataIndex = firstMetadataIndex(cleaned)

        val name = when {
            prescriptionFirst != null -> prescriptionFirst.groupValues[4].trimName()
            metadataIndex > 0 -> cleaned.substring(0, metadataIndex).trimName()
            markerCandidate && isLikelyExerciseName(cleaned) -> cleaned.trimName()
            else -> return null
        }
        if (!isLikelyExerciseName(name)) return null

        val parsed = ParsedWorkoutExercise(name = name, sourceLine = lineNumber).toDraft()
        applyMetadata(parsed, cleaned, lineNumber, warnings)
        if (prescriptionFirst != null) {
            assignSets(parsed, prescriptionFirst.groupValues[1], lineNumber, warnings)
            assignReps(
                parsed,
                prescriptionFirst.groupValues[2],
                prescriptionFirst.groupValues[3],
                lineNumber,
                warnings,
            )
        }
        return parsed.freeze()
    }

    private fun parseTableRow(
        cells: List<String>,
        header: TableHeader,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ): ParsedWorkoutExercise? {
        val name = cells.getOrNull(header.exercise)?.let(::cleanCell).orEmpty()
        if (!isLikelyExerciseName(name)) return null
        val draft = ParsedWorkoutExercise(name = name, sourceLine = lineNumber).toDraft()
        header.sets?.let { index -> cells.getOrNull(index)?.let { assignSets(draft, it, lineNumber, warnings) } }
        header.reps?.let { index -> cells.getOrNull(index)?.let { assignRepText(draft, it, lineNumber, warnings) } }
        header.load?.let { index -> cells.getOrNull(index)?.let { assignLoad(draft, it, lineNumber, warnings) } }
        header.rest?.let { index -> cells.getOrNull(index)?.let { assignRest(draft, it, lineNumber, warnings) } }
        header.rir?.let { index -> cells.getOrNull(index)?.let { assignRir(draft, it, lineNumber, warnings) } }
        header.type?.let { index -> cells.getOrNull(index)?.let { draft.setType = parseSetType(it) } }
        header.notes?.let { index -> cells.getOrNull(index)?.let { draft.notes = cleanCell(it) } }
        return draft.freeze()
    }

    private fun applyMetadata(
        draft: DraftExercise,
        text: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        SETS_REPS.find(text)?.let { match ->
            assignSets(draft, match.groupValues[1], lineNumber, warnings)
            assignReps(draft, match.groupValues[2], match.groupValues[3], lineNumber, warnings)
        } ?: SERIES_WITH_REPS.find(text)?.let { match ->
            assignSets(draft, match.groupValues[1], lineNumber, warnings)
            assignReps(draft, match.groupValues[2], match.groupValues[3], lineNumber, warnings)
        }

        if (draft.sets == null) {
            SETS_ONLY.find(text)?.let { assignSets(draft, it.groupValues[1], lineNumber, warnings) }
        }
        if (draft.repMin == null) {
            REPS_ONLY.find(text)?.let {
                assignReps(draft, it.groupValues[1], it.groupValues[2], lineNumber, warnings)
            }
        }
        LOAD.find(text)?.let { assignLoad(draft, it.groupValues[1], lineNumber, warnings) }
        (REST.find(text) ?: REST_SHORT.find(text))?.let {
            assignRest(draft, "${it.groupValues[1]} ${it.groupValues[2]}", lineNumber, warnings)
        }
        RIR.find(text)?.let { assignRir(draft, it.groupValues[1], lineNumber, warnings) }
        parseSetType(text)?.let { draft.setType = it }
        NOTES.find(text)?.let { match ->
            draft.notes = listOf(draft.notes, match.groupValues[1].trim()).filter(String::isNotBlank).joinToString(" • ")
        }
    }

    private fun assignSets(
        draft: DraftExercise,
        rawValue: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val value = rawValue.firstInteger()
        if (value == null || value !in 1..20) {
            warnings += WorkoutTextWarning(WorkoutTextWarningCode.INVALID_SETS, "Número de séries inválido e não importado.", lineNumber)
        } else {
            draft.sets = value
        }
    }

    private fun assignRepText(
        draft: DraftExercise,
        rawValue: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val range = NUMBER_RANGE.find(rawValue)
        if (range != null) {
            assignReps(draft, range.groupValues[1], range.groupValues[2], lineNumber, warnings)
        } else {
            val value = rawValue.firstInteger()
            assignReps(draft, value?.toString().orEmpty(), "", lineNumber, warnings)
        }
    }

    private fun assignReps(
        draft: DraftExercise,
        rawMin: String,
        rawMax: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val min = rawMin.firstInteger()
        val max = rawMax.firstInteger() ?: min
        if (min == null || max == null || min !in 1..100 || max !in 1..100 || min > max) {
            warnings += WorkoutTextWarning(WorkoutTextWarningCode.INVALID_REPS, "Faixa de repetições inválida e não importada.", lineNumber)
        } else {
            draft.repMin = min
            draft.repMax = max
        }
    }

    private fun assignLoad(
        draft: DraftExercise,
        rawValue: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val value = rawValue.firstDecimal()
        if (value == null || !value.isFinite() || value < 0.0 || value > 1_000.0) {
            warnings += WorkoutTextWarning(WorkoutTextWarningCode.INVALID_LOAD, "Carga inválida e não importada.", lineNumber)
        } else {
            draft.loadKg = value
        }
    }

    private fun assignRest(
        draft: DraftExercise,
        rawValue: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val value = rawValue.firstDecimal()
        val normalized = rawValue.normalized()
        val seconds = when {
            value == null -> null
            normalized.contains("min") -> (value * 60.0).toInt()
            else -> value.toInt()
        }
        if (seconds == null || seconds !in 0..3_600) {
            warnings += WorkoutTextWarning(WorkoutTextWarningCode.INVALID_REST, "Descanso inválido e não importado.", lineNumber)
        } else {
            draft.restSeconds = seconds
        }
    }

    private fun assignRir(
        draft: DraftExercise,
        rawValue: String,
        lineNumber: Int,
        warnings: MutableList<WorkoutTextWarning>,
    ) {
        val value = rawValue.firstInteger()
        if (value == null || value !in 0..10) {
            warnings += WorkoutTextWarning(WorkoutTextWarningCode.INVALID_RIR, "RIR inválido e não importado.", lineNumber)
        } else {
            draft.rir = value
        }
    }

    private fun parseSetType(text: String): ParsedSetType? {
        val normalized = text.normalized()
        return when {
            Regex("\\b(aquecimento|warm[ -]?up)\\b").containsMatchIn(normalized) -> ParsedSetType.WARM_UP
            Regex("\\b(drop[ -]?set)\\b").containsMatchIn(normalized) -> ParsedSetType.DROP_SET
            Regex("\\b(super[ -]?set|bi[ -]?set|serie conjugada)\\b").containsMatchIn(normalized) -> ParsedSetType.SUPER_SET
            Regex("\\b(ate a falha|falha|failure)\\b").containsMatchIn(normalized) -> ParsedSetType.FAILURE
            Regex("(?:tipo(?: de serie)?\\s*[:=-]?\\s*|\\b)normal\\b").containsMatchIn(normalized) -> ParsedSetType.NORMAL
            TYPE_LABEL.containsMatchIn(normalized) -> ParsedSetType.UNKNOWN
            else -> null
        }
    }

    private fun detectTableHeader(cells: List<String>): TableHeader? {
        val normalized = cells.map { it.normalized() }
        val exerciseIndex = normalized.indexOfFirst { it.matches(Regex(".*\\b(exercicio|movimento)\\b.*")) }
        if (exerciseIndex < 0) return null
        fun find(vararg names: String): Int? = normalized.indexOfFirst { cell -> names.any(cell::contains) }.takeIf { it >= 0 }
        return TableHeader(
            exercise = exerciseIndex,
            sets = find("series", "sets"),
            reps = find("reps", "repeticoes"),
            load = find("carga", "peso", "kg"),
            rest = find("descanso", "intervalo", "rest"),
            rir = find("rir"),
            type = find("tipo"),
            notes = find("observ", "notas"),
        )
    }

    private fun isWorkoutHeader(original: String, cleaned: String): Boolean {
        val normalized = cleaned.normalized()
        if (firstMetadataIndex(cleaned) >= 0) return false
        val markdownHeading = original.trimStart().startsWith("#")
        val explicit = normalized.matches(Regex("^(treino|workout|ficha|dia)\\b.*"))
        val day = extractWeekDays(cleaned).isNotEmpty()
        val letterSplit = normalized.matches(Regex("^[a-z0-9]{1,3}\\s*[-:]\\s*.+")) && original.trimStart().startsWith("#")
        return explicit || day || (markdownHeading && !isGenericSectionHeading(cleaned)) || letterSplit
    }

    private fun extractWeekDays(text: String): Set<DayOfWeek> {
        val normalized = text.normalized()
        return DAY_NAMES.mapNotNull { (regex, day) -> day.takeIf { regex.containsMatchIn(normalized) } }.toSet()
    }

    private fun containsOnlyWeekDays(text: String): Boolean {
        var remainder = text.normalized()
        DAY_NAMES.forEach { (regex, _) -> remainder = regex.replace(remainder, " ") }
        remainder = remainder.replace(Regex("(?:\\b(?:e|a|ate)\\b|[,;/&+\\s:()\\-–—])+"), "")
        return remainder.isBlank()
    }

    private fun isMetadataLine(text: String): Boolean = METADATA_LINE.containsMatchIn(text.normalized())

    private fun firstMetadataIndex(text: String): Int = listOf(
        SETS_REPS.find(text)?.range?.first,
        SERIES_WITH_REPS.find(text)?.range?.first,
        SETS_ONLY.find(text)?.range?.first,
        REPS_ONLY.find(text)?.range?.first,
        LOAD.find(text)?.range?.first,
        (REST.find(text) ?: REST_SHORT.find(text))?.range?.first,
        RIR.find(text)?.range?.first,
        TYPE_WORD.find(text.normalized())?.range?.first,
        NOTES.find(text)?.range?.first,
    ).filterNotNull().minOrNull() ?: -1

    private fun cleanMarkdownLine(value: String): String = value
        .trim()
        .replace(Regex("^>\\s*"), "")
        .replace(Regex("^#{1,6}\\s*"), "")
        .replace(Regex("^(?:[-*+]\\s+|\\d{1,3}[.)]\\s+)"), "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trim()

    private fun cleanCell(value: String): String = value.replace("**", "").replace("`", "").trim()

    private fun markdownCells(line: String): List<String> = line.trim().trim('|').split('|').map(String::trim)

    private fun isMarkdownSeparator(cells: List<String>): Boolean =
        cells.isNotEmpty() && cells.all { it.matches(Regex(":?-{3,}:?")) }

    private fun hasListMarker(value: String): Boolean = value.trimStart().matches(Regex("^(?:[-*+]\\s+|\\d{1,3}[.)]\\s+).+"))

    private fun hasStrongMarkdown(value: String): Boolean = value.contains("**") || value.trimStart().startsWith("#")

    private fun isLikelyExerciseName(value: String): Boolean {
        val name = value.trimName()
        val normalized = name.normalized()
        if (name.length !in 2..100 || name.none(Char::isLetter) || name.split(Regex("\\s+")).size > 14) return false
        if (isGenericSectionHeading(name) || METADATA_LINE.containsMatchIn(normalized)) return false
        return !normalized.matches(Regex("^(objetivo|foco|duracao|frequencia|instrucoes?|orientacoes?|dica|importante)\\b.*"))
    }

    private fun isGenericSectionHeading(value: String): Boolean {
        val normalized = value.trim().trimEnd(':').normalized()
        return normalized in setOf(
            "plano de treino", "ficha de treino", "exercicios", "exercicio", "observacoes",
            "orientacoes", "instrucoes", "aquecimento", "volta a calma", "notas", "legenda",
        )
    }

    private fun isNarrativeLine(value: String): Boolean {
        val normalized = value.normalized()
        return normalized.startsWith("observacao geral") || normalized.startsWith("lembre-se") ||
            normalized.startsWith("consulte ") || normalized.startsWith("objetivo:") ||
            (value.endsWith('.') && value.split(Regex("\\s+")).size > 8)
    }

    private fun isDecorativeSeparator(value: String): Boolean = value.matches(Regex("[-_=*•·]{3,}"))

    private fun String.trimName(): String = trim().trim(' ', '-', '–', '—', ':', ';', '|').trim()

    private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('×', 'x')

    private fun String.firstInteger(): Int? = Regex("\\d{1,4}").find(this)?.value?.toIntOrNull()

    private fun String.firstDecimal(): Double? = Regex("\\d{1,4}(?:[.,]\\d{1,2})?")
        .find(this)?.value?.replace(',', '.')?.toDoubleOrNull()

    private data class TableHeader(
        val exercise: Int,
        val sets: Int?,
        val reps: Int?,
        val load: Int?,
        val rest: Int?,
        val rir: Int?,
        val type: Int?,
        val notes: Int?,
    )

    private data class DraftWorkout(
        val name: String,
        var weekDays: Set<DayOfWeek>,
        val description: String = "",
        val exercises: MutableList<DraftExercise> = mutableListOf(),
    ) {
        fun freeze() = ParsedWorkout(name, description, exercises.map(DraftExercise::freeze), weekDays)
    }

    private data class DraftExercise(
        val name: String,
        var sets: Int?,
        var repMin: Int?,
        var repMax: Int?,
        var loadKg: Double?,
        var restSeconds: Int?,
        var rir: Int?,
        var setType: ParsedSetType?,
        var notes: String,
        val sourceLine: Int,
    ) {
        fun freeze() = ParsedWorkoutExercise(name, sets, repMin, repMax, loadKg, restSeconds, rir, setType, notes, sourceLine)
    }

    private fun ParsedWorkoutExercise.toDraft() = DraftExercise(
        name, sets, repMin, repMax, loadKg, restSeconds, rir, setType, notes, sourceLine,
    )

    private val SETS_REPS = Regex("(?i)\\b(\\d{1,3})\\s*[x×]\\s*(\\d{1,3})(?:\\s*(?:-|–|—|a|ate|até)\\s*(\\d{1,3}))?\\b")
    private val SERIES_WITH_REPS = Regex("(?i)\\b(\\d{1,3})\\s*(?:series?|séries?|sets?)\\s*(?:de|x)?\\s*(\\d{1,3})(?:\\s*(?:-|–|—|a|ate|até)\\s*(\\d{1,3}))?\\s*(?:reps?|repeticoes|repetições)?")
    private val SETS_ONLY = Regex("(?i)\\b(?:series?|séries?|sets?)\\s*[:=-]?\\s*(\\d{1,3})\\b")
    private val REPS_ONLY = Regex("(?i)\\b(?:reps?|repeticoes|repetições)\\s*[:=-]?\\s*(\\d{1,3})(?:\\s*(?:-|–|—|a|ate|até)\\s*(\\d{1,3}))?\\b")
    private val NUMBER_RANGE = Regex("(?i)(\\d{1,3})(?:\\s*(?:-|–|—|a|ate|até)\\s*(\\d{1,3}))?")
    private val LOAD = Regex("(?i)(?:\\b(?:carga|peso)\\s*[:=@-]?\\s*)?(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*kg\\b")
    private val REST = Regex("(?i)\\b(?:descanso|intervalo|rest)\\s*[:=-]?\\s*(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(s|seg(?:undo)?s?|min(?:uto)?s?)\\b")
    private val REST_SHORT = Regex("(?i)(?:^|[|;•]\\s*)(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(s|seg(?:undo)?s?|min(?:uto)?s?)\\b")
    private val RIR = Regex("(?i)\\bRIR\\s*[:=@-]?\\s*(\\d{1,2})\\+?")
    private val NOTES = Regex("(?i)\\b(?:obs(?:ervacoes|ervações)?|notas?)\\s*[:=-]\\s*(.+)$")
    private val TYPE_LABEL = Regex("\\b(?:tipo|tipo de serie)\\s*[:=-]")
    private val TYPE_WORD = Regex("\\b(?:aquecimento|warm[ -]?up|drop[ -]?set|super[ -]?set|bi[ -]?set|falha|failure)\\b")
    private val METADATA_LINE = Regex("^(?:series?|sets?|reps?|repeticoes|carga|peso|descanso|intervalo|rest|rir|tipo|obs|observacoes|notas?)\\b")
    private val PRESCRIPTION_FIRST = Regex("(?i)^(\\d{1,2})\\s*[x×]\\s*(\\d{1,3})(?:\\s*(?:-|–|—|a|ate|até)\\s*(\\d{1,3}))?\\s+(?:[-–—:]\\s*)?(.+)$")
    private val DAY_NAMES = listOf(
        Regex("\\b(?:segunda(?:-feira)?|seg)\\b") to DayOfWeek.MONDAY,
        Regex("\\b(?:terca(?:-feira)?|ter)\\b") to DayOfWeek.TUESDAY,
        Regex("\\b(?:quarta(?:-feira)?|qua)\\b") to DayOfWeek.WEDNESDAY,
        Regex("\\b(?:quinta(?:-feira)?|qui)\\b") to DayOfWeek.THURSDAY,
        Regex("\\b(?:sexta(?:-feira)?|sex)\\b") to DayOfWeek.FRIDAY,
        Regex("\\b(?:sabado|sab)\\b") to DayOfWeek.SATURDAY,
        Regex("\\b(?:domingo|dom)\\b") to DayOfWeek.SUNDAY,
    )
}
