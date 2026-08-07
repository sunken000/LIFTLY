package com.liftly.app.sharing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.liftly.app.data.SharedWorkoutPackage
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/** Human-readable representation used by [WorkoutPdfExporter]. */
data class WorkoutPdfContent(
    val workoutName: String,
    val description: String = "",
    val weekDays: List<String> = emptyList(),
    val exercises: List<WorkoutPdfExercise>,
    val qrCode: Bitmap? = null
)

data class WorkoutPdfExercise(
    val name: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetLoadKg: Double,
    val restSeconds: Int,
    val notes: String = "",
    val setType: String = "Normal",
    val trackingMode: String = "Repetições"
)

/**
 * Writes an A4-like, paginated workout sheet through Android's native [PdfDocument].
 * The caller owns the supplied [OutputStream]; this exporter flushes neither nor closes it.
 */
object WorkoutPdfExporter {
    @JvmOverloads
    fun write(
        output: OutputStream,
        shared: SharedWorkoutPackage,
        qrBitmap: Bitmap? = null
    ) {
        val exercisesByReference = shared.exercises.associateBy { it.referenceId }
        val content = WorkoutPdfContent(
            workoutName = shared.workout.name,
            description = shared.workout.description,
            weekDays = parseWeekDays(shared.workout.weekDays),
            exercises = shared.items.sortedBy { it.orderIndex }.map { item ->
                val exercise = requireNotNull(exercisesByReference[item.exerciseReferenceId]) {
                    "Exercício não encontrado no pacote: ${item.exerciseReferenceId}."
                }
                WorkoutPdfExercise(
                    name = exercise.name,
                    sets = item.sets,
                    repMin = item.repMin,
                    repMax = item.repMax,
                    targetLoadKg = item.targetLoadKg,
                    restSeconds = item.restSeconds,
                    notes = item.notes,
                    setType = item.setType,
                    trackingMode = item.trackingMode
                )
            },
            qrCode = qrBitmap
        )
        write(content, output)
    }

    @JvmOverloads
    fun write(
        outputFile: File,
        shared: SharedWorkoutPackage,
        qrBitmap: Bitmap? = null
    ) {
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().buffered().use { output ->
            write(output, shared, qrBitmap)
        }
    }

    fun write(content: WorkoutPdfContent, output: OutputStream) {
        require(content.workoutName.isNotBlank()) { "O treino precisa ter um nome." }
        require(content.exercises.none { it.name.isBlank() }) {
            "Todos os exercícios precisam ter um nome."
        }

        val document = PdfDocument()
        try {
            PdfRenderer(document).render(content)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    fun write(content: WorkoutPdfContent, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().buffered().use { output ->
            write(content, output)
        }
    }

    private fun parseWeekDays(raw: String): List<String> {
        val labels = mapOf(
            1 to "Segunda",
            2 to "Terça",
            3 to "Quarta",
            4 to "Quinta",
            5 to "Sexta",
            6 to "Sábado",
            7 to "Domingo"
        )
        return raw.split(',')
            .mapNotNull { token -> token.trim().toIntOrNull() }
            .distinct()
            .sorted()
            .mapNotNull(labels::get)
    }

    private class PdfRenderer(private val document: PdfDocument) {
        private val locale = Locale.forLanguageTag("pt-BR")
        private val numberFormat = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }

        private val brandPaint = textPaint(10f, DARK, Typeface.BOLD).apply {
            letterSpacing = 0.16f
        }
        private val titlePaint = textPaint(22f, DARK, Typeface.BOLD)
        private val continuationTitlePaint = textPaint(11f, DARK, Typeface.BOLD)
        private val descriptionPaint = textPaint(10f, MUTED)
        private val sectionPaint = textPaint(14f, DARK, Typeface.BOLD)
        private val exerciseNamePaint = textPaint(12f, DARK, Typeface.BOLD)
        private val exerciseMetaPaint = textPaint(9.5f, MUTED)
        private val notePaint = textPaint(9f, DARK)
        private val smallPaint = textPaint(7.5f, MUTED)
        private val footerPaint = textPaint(7.5f, MUTED)
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RULE
            strokeWidth = 1f
        }
        private val qrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }

        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var pageNumber = 0
        private var y = TOP_MARGIN
        private var workoutName = ""

        fun render(content: WorkoutPdfContent) {
            workoutName = content.workoutName.trim()
            startPage(firstPage = true)
            drawFirstPageHeader(content)

            ensureSpace(36f)
            canvas.drawText("Exercícios", LEFT_MARGIN, y + sectionPaint.textSize, sectionPaint)
            y += 29f

            if (content.exercises.isEmpty()) {
                drawWrappedPaginated(
                    text = "Nenhum exercício foi adicionado a este treino.",
                    paint = descriptionPaint,
                    maxWidth = CONTENT_WIDTH
                )
            } else {
                content.exercises.forEachIndexed { index, exercise ->
                    drawExercise(index + 1, exercise)
                }
            }

            finishPage()
        }

        private fun drawFirstPageHeader(content: WorkoutPdfContent) {
            canvas.drawText("LIFTLY", LEFT_MARGIN, y + brandPaint.textSize, brandPaint)
            y += 31f

            val qr = content.qrCode?.takeUnless { it.isRecycled }
            val textWidth = if (qr == null) CONTENT_WIDTH else CONTENT_WIDTH - QR_SIZE - 18f
            val qrTop = if (qr == null) 0f else y - 5f
            if (qr != null) {
                val qrLeft = PAGE_WIDTH - RIGHT_MARGIN - QR_SIZE
                canvas.drawRoundRect(
                    qrLeft - 5f,
                    qrTop - 5f,
                    qrLeft + QR_SIZE + 5f,
                    qrTop + QR_SIZE + 19f,
                    5f,
                    5f,
                    cardPaint
                )
                canvas.drawBitmap(
                    qr,
                    null,
                    RectF(qrLeft, qrTop, qrLeft + QR_SIZE, qrTop + QR_SIZE),
                    qrPaint
                )
                val label = "Escaneie para importar"
                val labelX = qrLeft + (QR_SIZE - smallPaint.measureText(label)) / 2f
                canvas.drawText(label, labelX, qrTop + QR_SIZE + 13f, smallPaint)
            }

            wrapText(workoutName, titlePaint, textWidth).forEach { line ->
                canvas.drawText(line, LEFT_MARGIN, y + titlePaint.textSize, titlePaint)
                y += lineHeight(titlePaint)
            }

            if (content.weekDays.isNotEmpty()) {
                y += 3f
                val days = "Dias: " + content.weekDays
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
                wrapText(days, descriptionPaint, textWidth).forEach { line ->
                    canvas.drawText(line, LEFT_MARGIN, y + descriptionPaint.textSize, descriptionPaint)
                    y += lineHeight(descriptionPaint)
                }
            }

            if (content.description.isNotBlank()) {
                y += 5f
                wrapText(content.description.trim(), descriptionPaint, textWidth).forEach { line ->
                    canvas.drawText(line, LEFT_MARGIN, y + descriptionPaint.textSize, descriptionPaint)
                    y += lineHeight(descriptionPaint)
                }
            }

            if (qr != null) {
                y = max(y, qrTop + QR_SIZE + 24f)
            }
            y += 12f
            canvas.drawLine(LEFT_MARGIN, y, PAGE_WIDTH - RIGHT_MARGIN, y, rulePaint)
            y += 15f
        }

        private fun drawExercise(position: Int, exercise: WorkoutPdfExercise) {
            val lines = buildList {
                wrapText("$position. ${exercise.name.trim()}", exerciseNamePaint, CARD_TEXT_WIDTH)
                    .forEach { add(Line(it, exerciseNamePaint)) }

                val reps = if (exercise.repMin == exercise.repMax) {
                    "${exercise.repMin} reps"
                } else {
                    "${exercise.repMin}–${exercise.repMax} reps"
                }
                val load = if (exercise.targetLoadKg > 0.0) {
                    "${numberFormat.format(exercise.targetLoadKg)} kg"
                } else {
                    "carga livre"
                }
                val rest = formatDuration(exercise.restSeconds)
                val metrics = "${exercise.sets} séries • $reps • $load • descanso $rest"
                wrapText(metrics, exerciseMetaPaint, CARD_TEXT_WIDTH)
                    .forEach { add(Line(it, exerciseMetaPaint)) }

                val tags = listOf(exercise.setType, exercise.trackingMode)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (tags.isNotEmpty()) {
                    wrapText(tags.joinToString(" • "), exerciseMetaPaint, CARD_TEXT_WIDTH)
                        .forEach { add(Line(it, exerciseMetaPaint)) }
                }

                if (exercise.notes.isNotBlank()) {
                    wrapText(
                        "Observações: ${exercise.notes.trim()}",
                        notePaint,
                        CARD_TEXT_WIDTH
                    ).forEach { add(Line(it, notePaint)) }
                }
            }

            drawCardAcrossPages(exercise.name.trim(), lines)
        }

        private fun drawCardAcrossPages(exerciseName: String, originalLines: List<Line>) {
            var remaining = originalLines
            var continuation = false

            while (remaining.isNotEmpty()) {
                if (availableHeight() < 58f) {
                    startPage(firstPage = false)
                }

                val header = if (continuation) {
                    wrapText(
                        "$exerciseName (continuação)",
                        exerciseNamePaint,
                        CARD_TEXT_WIDTH
                    ).map { Line(it, exerciseNamePaint) }
                } else {
                    emptyList()
                }
                val capacity = availableHeight() - CARD_VERTICAL_PADDING * 2f
                var used = header.sumOf { it.height.toDouble() }.toFloat()
                var takeCount = 0
                for (line in remaining) {
                    if (used + line.height > capacity && takeCount > 0) break
                    if (used + line.height > capacity) break
                    used += line.height
                    takeCount++
                }

                if (takeCount == 0) {
                    startPage(firstPage = false)
                    continue
                }

                val segment = header + remaining.take(takeCount)
                drawCardSegment(segment)
                remaining = remaining.drop(takeCount)
                continuation = remaining.isNotEmpty()
                if (continuation) {
                    startPage(firstPage = false)
                }
            }
        }

        private fun drawCardSegment(lines: List<Line>) {
            val height = lines.sumOf { it.height.toDouble() }.toFloat() + CARD_VERTICAL_PADDING * 2f
            val bottom = y + height
            canvas.drawRoundRect(
                LEFT_MARGIN,
                y,
                PAGE_WIDTH - RIGHT_MARGIN,
                bottom,
                8f,
                8f,
                cardPaint
            )
            canvas.drawRoundRect(
                LEFT_MARGIN,
                y,
                LEFT_MARGIN + 4f,
                bottom,
                4f,
                4f,
                accentPaint
            )

            var lineY = y + CARD_VERTICAL_PADDING
            lines.forEach { line ->
                canvas.drawText(line.text, CARD_TEXT_LEFT, lineY + line.paint.textSize, line.paint)
                lineY += line.height
            }
            y = bottom + CARD_GAP
        }

        private fun drawWrappedPaginated(text: String, paint: Paint, maxWidth: Float) {
            wrapText(text, paint, maxWidth).forEach { line ->
                ensureSpace(lineHeight(paint))
                canvas.drawText(line, LEFT_MARGIN, y + paint.textSize, paint)
                y += lineHeight(paint)
            }
        }

        private fun ensureSpace(required: Float) {
            if (availableHeight() < required) startPage(firstPage = false)
        }

        private fun availableHeight(): Float = CONTENT_BOTTOM - y

        private fun startPage(firstPage: Boolean) {
            finishPage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber)
                .create()
            page = document.startPage(info)
            canvas = requireNotNull(page).canvas
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(0f, 0f, PAGE_WIDTH, 6f, accentPaint)
            y = TOP_MARGIN

            if (!firstPage) {
                canvas.drawText("LIFTLY", LEFT_MARGIN, y + brandPaint.textSize, brandPaint)
                val suffix = " • $workoutName"
                canvas.drawText(
                    suffix,
                    LEFT_MARGIN + brandPaint.measureText("LIFTLY"),
                    y + continuationTitlePaint.textSize,
                    continuationTitlePaint
                )
                y += 30f
                canvas.drawLine(LEFT_MARGIN, y, PAGE_WIDTH - RIGHT_MARGIN, y, rulePaint)
                y += 14f
            }
        }

        private fun finishPage() {
            val current = page ?: return
            canvas.drawLine(
                LEFT_MARGIN,
                PAGE_HEIGHT - FOOTER_MARGIN - 13f,
                PAGE_WIDTH - RIGHT_MARGIN,
                PAGE_HEIGHT - FOOTER_MARGIN - 13f,
                rulePaint
            )
            val footer = "Liftly • Página $pageNumber"
            canvas.drawText(
                footer,
                PAGE_WIDTH - RIGHT_MARGIN - footerPaint.measureText(footer),
                PAGE_HEIGHT - FOOTER_MARGIN,
                footerPaint
            )
            document.finishPage(current)
            page = null
        }

        private fun formatDuration(totalSeconds: Int): String {
            if (totalSeconds <= 0) return "livre"
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return when {
                minutes == 0 -> "$seconds s"
                seconds == 0 -> "$minutes min"
                else -> "$minutes min $seconds s"
            }
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return emptyList()
            return buildList {
                text.replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .split('\n')
                    .forEach { paragraph ->
                        if (paragraph.isBlank()) {
                            add("")
                        } else {
                            var current = ""
                            paragraph.trim().split(Regex("\\s+")).forEach { word ->
                                val parts = splitLongWord(word, paint, maxWidth)
                                parts.forEach { part ->
                                    val candidate = if (current.isEmpty()) part else "$current $part"
                                    if (paint.measureText(candidate) <= maxWidth) {
                                        current = candidate
                                    } else {
                                        if (current.isNotEmpty()) add(current)
                                        current = part
                                    }
                                }
                            }
                            if (current.isNotEmpty()) add(current)
                        }
                    }
            }
        }

        private fun splitLongWord(word: String, paint: Paint, maxWidth: Float): List<String> {
            if (paint.measureText(word) <= maxWidth) return listOf(word)
            return buildList {
                var start = 0
                while (start < word.length) {
                    var end = start + 1
                    while (end <= word.length && paint.measureText(word, start, end) <= maxWidth) {
                        end++
                    }
                    val fittingEnd = max(start + 1, end - 1)
                    add(word.substring(start, fittingEnd))
                    start = fittingEnd
                }
            }
        }

        private data class Line(val text: String, val paint: Paint) {
            val height: Float = lineHeight(paint)
        }

        companion object {
            private const val PAGE_WIDTH = 595f
            private const val PAGE_HEIGHT = 842f
            private const val LEFT_MARGIN = 40f
            private const val RIGHT_MARGIN = 40f
            private const val TOP_MARGIN = 27f
            private const val FOOTER_MARGIN = 24f
            private const val CONTENT_BOTTOM = PAGE_HEIGHT - FOOTER_MARGIN - 21f
            private const val CONTENT_WIDTH = PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN
            private const val QR_SIZE = 92f
            private const val CARD_TEXT_LEFT = LEFT_MARGIN + 14f
            private const val CARD_TEXT_WIDTH = CONTENT_WIDTH - 28f
            private const val CARD_VERTICAL_PADDING = 9f
            private const val CARD_GAP = 9f

            private val DARK = 0xFF102126.toInt()
            private val MUTED = 0xFF52676D.toInt()
            private val ACCENT = 0xFF12BFC5.toInt()
            private val CARD = 0xFFF2F7F7.toInt()
            private val RULE = 0xFFD5E0E1.toInt()

            private fun textPaint(size: Float, colorValue: Int, style: Int = Typeface.NORMAL) =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorValue
                    textSize = size
                    typeface = Typeface.create(Typeface.SANS_SERIF, style)
                }

            private fun lineHeight(paint: Paint): Float = paint.textSize * 1.38f
        }
    }
}
