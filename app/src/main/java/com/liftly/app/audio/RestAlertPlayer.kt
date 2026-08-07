package com.liftly.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Plays the selected rest alert through the media route (including connected headphones).
 * A transient-may-duck focus request temporarily lowers compatible music players.
 */
class RestAlertPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var audioTrack: AudioTrack? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var completion: Runnable? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                -> stop()

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    runCatching { audioTrack?.setVolume(DUCKED_ALERT_VOLUME) }
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    runCatching { audioTrack?.setVolume(ALERT_VOLUME) }
                }
            }
        }
    }

    enum class PlayResult {
        STARTED,
        MEDIA_VOLUME_MUTED,
        AUDIO_FOCUS_DENIED,
        OUTPUT_ERROR,
    }

    /** Returns a diagnostic result so the preview button never fails silently. */
    @Synchronized
    fun play(soundId: String, durationSeconds: Int): PlayResult {
        stop()
        val safeDuration = durationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        if (mediaVolumePercent() <= 0) return PlayResult.MEDIA_VOLUME_MUTED

        val sound = RestAlertSound.fromId(soundId)
        val samples = renderRestAlert(sound, safeDuration, SAMPLE_RATE)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusChangeListener, handler)
            .build()

        val focusResult = runCatching { audioManager.requestAudioFocus(focusRequest) }
            .onFailure { error -> Log.e(LOG_TAG, "Audio focus request failed.", error) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return PlayResult.AUDIO_FOCUS_DENIED
        }
        audioFocusRequest = focusRequest

        val started = runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            check(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack was not initialized." }
            var written = 0
            while (written < samples.size) {
                val count = track.write(
                    samples,
                    written,
                    samples.size - written,
                    AudioTrack.WRITE_BLOCKING,
                )
                check(count > 0) { "AudioTrack write failed with code $count." }
                written += count
            }
            track.setVolume(ALERT_VOLUME)
            audioTrack = track
            track.play()
            check(track.playState == AudioTrack.PLAYSTATE_PLAYING) { "AudioTrack did not start." }
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "PCM alert failed; trying system tone fallback.", error)
            releaseAudioTrack()
            runCatching { startToneFallback(sound, safeDuration) }
                .onFailure { fallbackError ->
                    Log.e(LOG_TAG, "Rest alert fallback failed.", fallbackError)
                }
                .getOrDefault(false)
        }

        if (!started) {
            stop()
            return PlayResult.OUTPUT_ERROR
        }

        acquireWakeLock(safeDuration)
        val finish = Runnable { stop() }
        completion = finish
        handler.postDelayed(finish, safeDuration * 1_000L + RELEASE_GRACE_MILLIS)
        return PlayResult.STARTED
    }

    fun mediaVolumePercent(): Int {
        if (audioManager.isVolumeFixed) return 100
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximum <= 0) return 0
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current * 100.0) / maximum).toInt().coerceIn(0, 100)
    }

    @Synchronized
    fun stop() {
        completion?.let(handler::removeCallbacks)
        completion = null

        releaseAudioTrack()
        toneGenerator?.let { generator ->
            runCatching { generator.stopTone() }
            runCatching { generator.release() }
        }
        toneGenerator = null

        val focusRequest = audioFocusRequest
        audioFocusRequest = null
        if (focusRequest != null) {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }

        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    private fun acquireWakeLock(durationSeconds: Int) {
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(durationSeconds * 1_000L + WAKE_LOCK_GRACE_MILLIS)
        }
    }

    private fun startToneFallback(sound: RestAlertSound, durationSeconds: Int): Boolean {
        val toneType = when (sound) {
            RestAlertSound.GENTLE -> ToneGenerator.TONE_PROP_BEEP
            RestAlertSound.PULSE -> ToneGenerator.TONE_CDMA_PIP
            RestAlertSound.ASCENDING -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            RestAlertSound.CHIME -> ToneGenerator.TONE_CDMA_ABBR_ALERT
        }
        val generator = ToneGenerator(AudioManager.STREAM_MUSIC, FALLBACK_VOLUME_PERCENT)
        toneGenerator = generator
        return generator.startTone(toneType, durationSeconds * 1_000)
    }

    private fun releaseAudioTrack() {
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching { if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop() }
            runCatching { track.release() }
        }
    }

    companion object {
        const val MIN_DURATION_SECONDS = 1
        const val MAX_DURATION_SECONDS = 10

        private const val SAMPLE_RATE = 22_050
        private const val ALERT_VOLUME = 1.0f
        private const val DUCKED_ALERT_VOLUME = 0.35f
        private const val FALLBACK_VOLUME_PERCENT = 100
        private const val RELEASE_GRACE_MILLIS = 150L
        private const val WAKE_LOCK_GRACE_MILLIS = 1_000L
        private const val WAKE_LOCK_TAG = "Liftly::RestAlert"
        private const val LOG_TAG = "LiftlyRestAlert"
    }
}
