package com.example.recorderproject.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

enum class BtScoState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class BluetoothMonitor(private val context: Context) {
    private val TAG = "BluetoothMonitor"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var scoReceiver: BroadcastReceiver? = null
    private var sampleRate = 48000

    var onStateChange: ((BtScoState) -> Unit)? = null

    fun start(sampleRate: Int) {
        this.sampleRate = sampleRate
        Log.d(TAG, "Starting Bluetooth SCO at ${sampleRate}Hz")
        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                Log.d(TAG, "SCO state: $state")
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        setupAudioTrack()
                        onStateChange?.invoke(BtScoState.CONNECTED)
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        releaseAudioTrack()
                        onStateChange?.invoke(BtScoState.DISCONNECTED)
                    }
                    AudioManager.SCO_AUDIO_STATE_ERROR -> {
                        onStateChange?.invoke(BtScoState.ERROR)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        }
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()
            onStateChange?.invoke(BtScoState.CONNECTING)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied: ${e.message}")
            onStateChange?.invoke(BtScoState.ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BT SCO: ${e.message}")
            onStateChange?.invoke(BtScoState.ERROR)
        }
    }

    fun write(samples: ShortArray, count: Int) {
        val track = audioTrack ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            try { track.write(samples, 0, count) } catch (_: Exception) {}
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping Bluetooth SCO")
        releaseAudioTrack()
        try { context.unregisterReceiver(scoReceiver) } catch (_: Exception) {}
        scoReceiver = null
        try {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        onStateChange?.invoke(BtScoState.DISCONNECTED)
    }

    private fun setupAudioTrack() {
        releaseAudioTrack()
        try {
            val bufSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            @Suppress("DEPRECATION")
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufSize, AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
            Log.d(TAG, "AudioTrack playing")
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed: ${e.message}")
            onStateChange?.invoke(BtScoState.ERROR)
        }
    }

    private fun releaseAudioTrack() {
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }
}
