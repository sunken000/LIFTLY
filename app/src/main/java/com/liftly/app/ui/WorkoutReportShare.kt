package com.liftly.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.liftly.app.domain.WorkoutReport
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Gera um card local 1080x1350; nenhum dado é enviado a servidor. */
object WorkoutReportShare {
    fun share(context: Context, report: WorkoutReport) {
        val bitmap = render(report)
        val dir = File(context.cacheDir, "workout_reports").apply { mkdirs() }
        val file = File(dir, "liftly-${report.sessionId}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${report.workoutName} • Liftly")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar treino"))
    }

    private fun render(report: WorkoutReport): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply { color = Color.rgb(13, 13, 18) }
        val violet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(151, 111, 255) }
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(173, 172, 183) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        canvas.drawRect(70f, 72f, 82f, 1278f, violet)

        fun text(value: String, x: Float, y: Float, size: Float, paint: Paint = white, bold: Boolean = false) {
            paint.textSize = size
            paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }

        text("LIFTLY / SESSÃO", 122f, 126f, 34f, violet, true)
        text(report.workoutName.take(28), 122f, 224f, 66f, white, true)
        text(report.coachHeadline.take(48), 122f, 292f, 30f, muted, true)

        val metricsY = 430f
        text(report.durationMinutes.toString(), 122f, metricsY, 72f, white, true)
        text("MIN", 122f, metricsY + 38f, 24f, muted, true)
        text("${report.completedSets}/${report.totalSets}", 390f, metricsY, 72f, white, true)
        text("SÉRIES", 390f, metricsY + 38f, 24f, muted, true)
        text(formatKg(report.volumeKg), 690f, metricsY, 58f, white, true)
        text("VOLUME", 690f, metricsY + 38f, 24f, muted, true)

        canvas.drawRect(122f, 540f, 1010f, 544f, violet)
        text("DESTAQUES", 122f, 610f, 26f, violet, true)
        var y = 680f
        val highlights = buildList {
            if (report.personalRecords > 0) add("${report.personalRecords} recorde(s) pessoal(is)")
            report.volumeDeltaPercent?.let { add("Volume ${if (it >= 0) "+" else ""}$it% vs. sessão anterior") }
            report.exercises.take(4).forEach { exercise ->
                add("${exercise.name.take(24)}  ${formatKg(exercise.bestLoadKg)} × ${exercise.bestReps}")
            }
        }.ifEmpty { listOf("Histórico atualizado para a próxima sessão") }
        highlights.take(6).forEachIndexed { index, line ->
            text((index + 1).toString().padStart(2, '0'), 122f, y, 28f, violet, true)
            text(line, 185f, y, 31f, white, true)
            y += 78f
        }

        text("Treine. Registre. Ajuste.", 122f, 1220f, 34f, white, true)
        text("Dados locais • Liftly", 122f, 1270f, 24f, muted)
        return bitmap
    }

    private fun formatKg(value: Double): String = if (value >= 1000.0) {
        String.format(Locale.US, "%.1fk", value / 1000.0)
    } else if (value % 1.0 == 0.0) {
        "${value.toInt()} kg"
    } else {
        String.format(Locale.US, "%.1f kg", value)
    }
}
