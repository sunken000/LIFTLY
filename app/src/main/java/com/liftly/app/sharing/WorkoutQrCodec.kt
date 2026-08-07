package com.liftly.app.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Creates and reads QR images without requiring a camera or a scanning activity.
 *
 * The payload is kept as an opaque UTF-8 string so this class can be used with any
 * versioned workout package. Callers can use [isWithinRecommendedSize] to decide when
 * a file is a better sharing option than a QR code.
 */
object WorkoutQrCodec {
    const val DEFAULT_SIZE_PX = 1_024
    const val MIN_SIZE_PX = 96
    const val MAX_SIZE_PX = 4_096

    /** Conservative capacity for a UTF-8 payload with medium error correction. */
    const val RECOMMENDED_MAX_PAYLOAD_BYTES = 2_200

    fun isWithinRecommendedSize(payload: String): Boolean =
        payload.toByteArray(StandardCharsets.UTF_8).size <= RECOMMENDED_MAX_PAYLOAD_BYTES

    @JvmOverloads
    fun encode(payload: String, size: Int = DEFAULT_SIZE_PX): Bitmap =
        encode(payload, WorkoutQrOptions(sizePx = size))

    fun encode(payload: String, options: WorkoutQrOptions): Bitmap {
        require(payload.isNotEmpty()) { "O conteúdo do QR não pode estar vazio." }
        require(options.sizePx in MIN_SIZE_PX..MAX_SIZE_PX) {
            "O QR deve ter entre $MIN_SIZE_PX e $MAX_SIZE_PX pixels."
        }
        require(options.marginModules >= 0) { "A margem do QR não pode ser negativa." }
        require(options.foregroundColor != options.backgroundColor) {
            "As cores de frente e fundo do QR precisam ser diferentes."
        }

        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION to options.errorCorrection.toZxing(),
            EncodeHintType.MARGIN to options.marginModules
        )
        val matrix = try {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                options.sizePx,
                options.sizePx,
                hints
            )
        } catch (error: WriterException) {
            throw WorkoutQrEncodingException(
                "O conteúdo não cabe no QR. Compartilhe o arquivo do treino.",
                error
            )
        }

        val pixels = IntArray(options.sizePx * options.sizePx)
        for (y in 0 until options.sizePx) {
            val rowOffset = y * options.sizePx
            for (x in 0 until options.sizePx) {
                pixels[rowOffset + x] = if (matrix[x, y]) {
                    options.foregroundColor
                } else {
                    options.backgroundColor
                }
            }
        }

        return Bitmap.createBitmap(
            pixels,
            options.sizePx,
            options.sizePx,
            Bitmap.Config.ARGB_8888
        )
    }

    @JvmOverloads
    fun encodePng(payload: String, options: WorkoutQrOptions = WorkoutQrOptions()): ByteArray =
        ByteArrayOutputStream().use { output ->
            writePng(payload, output, options)
            output.toByteArray()
        }

    @JvmOverloads
    fun writePng(
        payload: String,
        output: OutputStream,
        options: WorkoutQrOptions = WorkoutQrOptions()
    ) {
        val bitmap = encode(payload, options)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Não foi possível gravar a imagem PNG do QR."
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun decode(bitmap: Bitmap): String {
        require(!bitmap.isRecycled) { "A imagem do QR já foi descartada." }
        require(bitmap.width > 0 && bitmap.height > 0) { "A imagem do QR está vazia." }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
            DecodeHintType.TRY_HARDER to true
        )
        val reader = MultiFormatReader().apply { setHints(hints) }

        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            reader.reset()
            try {
                reader.setHints(hints)
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text
            } catch (error: NotFoundException) {
                throw WorkoutQrDecodingException(
                    "Nenhum QR de treino legível foi encontrado na imagem.",
                    error
                )
            }
        } finally {
            reader.reset()
        }
    }

    fun decodePng(bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "A imagem PNG está vazia." }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw WorkoutQrDecodingException("O arquivo não contém uma imagem válida.")
        return try {
            decode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun decodePng(input: InputStream): String {
        val bitmap = BitmapFactory.decodeStream(input)
            ?: throw WorkoutQrDecodingException("O arquivo não contém uma imagem válida.")
        return try {
            decode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}

data class WorkoutQrOptions(
    val sizePx: Int = WorkoutQrCodec.DEFAULT_SIZE_PX,
    val marginModules: Int = 2,
    val foregroundColor: Int = Color.BLACK,
    val backgroundColor: Int = Color.WHITE,
    val errorCorrection: WorkoutQrErrorCorrection = WorkoutQrErrorCorrection.MEDIUM
)

enum class WorkoutQrErrorCorrection {
    LOW,
    MEDIUM,
    QUARTILE,
    HIGH;

    internal fun toZxing(): ErrorCorrectionLevel = when (this) {
        LOW -> ErrorCorrectionLevel.L
        MEDIUM -> ErrorCorrectionLevel.M
        QUARTILE -> ErrorCorrectionLevel.Q
        HIGH -> ErrorCorrectionLevel.H
    }
}

class WorkoutQrEncodingException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class WorkoutQrDecodingException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
