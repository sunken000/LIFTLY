package com.liftly.app.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Sounds available for the end-of-rest alert. They are generated locally and work offline. */
enum class RestAlertSound(
    val id: String,
    val displayName: String,
) {
    GENTLE("suave", "Suave"),
    PULSE("pulso", "Pulso"),
    ASCENDING("ascendente", "Ascendente"),
    CHIME("sino", "Sino digital"),
    ;

    companion object {
        fun fromId(id: String?): RestAlertSound = entries.firstOrNull { it.id == id }
            ?: ASCENDING
    }
}

private const val DEFAULT_SAMPLE_RATE = 22_050
private const val BUFFER_DURATION_SECONDS = 1.0

/** Pure PCM synthesis kept separate from Android APIs so every alert can be unit-tested. */
internal fun synthesizeRestAlert(
    sound: RestAlertSound,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
): ShortArray {
    require(sampleRate >= 8_000) { "Sample rate must be at least 8 kHz." }
    val sampleCount = (sampleRate * BUFFER_DURATION_SECONDS).toInt()

    return ShortArray(sampleCount) { index ->
        val timeSeconds = index.toDouble() / sampleRate
        val value = when (sound) {
            RestAlertSound.GENTLE -> gentleSample(timeSeconds)
            RestAlertSound.PULSE -> pulseSample(timeSeconds)
            RestAlertSound.ASCENDING -> ascendingSample(timeSeconds)
            RestAlertSound.CHIME -> chimeSample(timeSeconds)
        }
        (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.78).toInt().toShort()
    }
}

/** Renders the full requested duration, avoiding device-dependent AudioTrack loop points. */
internal fun renderRestAlert(
    sound: RestAlertSound,
    durationSeconds: Int,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
): ShortArray {
    require(durationSeconds > 0) { "Duration must be positive." }
    val oneSecond = synthesizeRestAlert(sound, sampleRate)
    return ShortArray(oneSecond.size * durationSeconds) { index ->
        oneSecond[index % oneSecond.size]
    }
}

private fun gentleSample(time: Double): Double {
    if (time !in 0.04..0.78) return 0.0
    val local = time - 0.04
    val envelope = noteEnvelope(local, 0.74, attack = 0.025, release = 0.18) * exp(-local * 1.4)
    return envelope * (
        0.72 * oscillator(659.25, local) +
            0.18 * oscillator(1_318.5, local)
        )
}

private fun pulseSample(time: Double): Double =
    note(time, 0.05, 0.19, 783.99, 0.82) +
        note(time, 0.30, 0.44, 783.99, 0.82) +
        note(time, 0.55, 0.71, 987.77, 0.88)

private fun ascendingSample(time: Double): Double =
    note(time, 0.04, 0.22, 659.25, 0.78) +
        note(time, 0.27, 0.45, 783.99, 0.82) +
        note(time, 0.50, 0.78, 1_046.50, 0.90)

private fun chimeSample(time: Double): Double {
    if (time !in 0.04..0.88) return 0.0
    val local = time - 0.04
    val envelope = noteEnvelope(local, 0.84, attack = 0.008, release = 0.22) * exp(-local * 2.5)
    return envelope * (
        0.64 * oscillator(783.99, local) +
            0.25 * oscillator(1_175.0, local) +
            0.11 * oscillator(1_567.98, local)
        )
}

private fun note(
    time: Double,
    start: Double,
    end: Double,
    frequency: Double,
    amplitude: Double,
): Double {
    if (time !in start..end) return 0.0
    val local = time - start
    val duration = end - start
    val envelope = noteEnvelope(local, duration, attack = 0.012, release = 0.045)
    return amplitude * envelope * (
        0.86 * oscillator(frequency, local) +
            0.14 * oscillator(frequency * 2.0, local)
        )
}

private fun noteEnvelope(
    localTime: Double,
    duration: Double,
    attack: Double,
    release: Double,
): Double {
    val attackGain = (localTime / attack).coerceIn(0.0, 1.0)
    val releaseGain = ((duration - localTime) / release).coerceIn(0.0, 1.0)
    return attackGain * releaseGain
}

private fun oscillator(frequency: Double, time: Double): Double =
    sin(2.0 * PI * frequency * time)
