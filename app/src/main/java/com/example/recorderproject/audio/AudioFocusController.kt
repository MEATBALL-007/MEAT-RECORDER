package com.example.recorderproject.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Owns audio-focus acquisition/release and the ACTION_AUDIO_BECOMING_NOISY (headphone-unplug)
 * handling for the recording lifecycle. Extracted from RecorderViewModel (issue #13 step 7a).
 *
 * On audio-focus loss while recording it stops recording (via [onFocusLost]); on an output-route
 * change while recording it warns but keeps recording. Android-coupled, so verified by compile +
 * the app's existing suite + device smoke.
 *
 * @param isRecording reads the VM's current recording state.
 * @param onFocusLost forwards a focus-loss-while-recording event (→ VM stopRecording()).
 */
class AudioFocusController(
    private val app: Context,
    private val isRecording: () -> Boolean,
    private val onFocusLost: () -> Unit,
) {
    private var audioFocusRequest: AudioFocusRequest? = null
    private var becomingNoisyRegistered = false

    private val audioManager by lazy {
        app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isRecording()) {
                    Log.w(TAG, "Audio focus lost (change=$change) — stopping recording")
                    Toast.makeText(app, "Recording stopped: another app took audio focus", Toast.LENGTH_LONG).show()
                    onFocusLost()
                }
            }
            else -> {}
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isRecording()) {
                    Log.w(TAG, "Audio output route changed (headphones unplugged) during recording")
                    Toast.makeText(app, "Headphones unplugged — recording continues on built-in mic", Toast.LENGTH_LONG).show()
                    // Note: we don't stop recording — the user might want it to continue.
                    // Just warn so they know the route changed.
                }
            }
        }
    }

    /** Request audio focus. Returns true if granted. Best-effort — callers don't block on it. */
    fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    /** Register the becoming-noisy receiver (call when recording starts). Idempotent. */
    fun registerBecomingNoisy() {
        if (!becomingNoisyRegistered) {
            app.registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            )
            becomingNoisyRegistered = true
        }
    }

    /** Unregister the becoming-noisy receiver (call on stop / onCleared). Idempotent. */
    fun unregisterBecomingNoisy() {
        if (becomingNoisyRegistered) {
            try { app.unregisterReceiver(becomingNoisyReceiver) } catch (_: Exception) {}
            becomingNoisyRegistered = false
        }
    }

    companion object { private const val TAG = "AudioFocusController" }
}
