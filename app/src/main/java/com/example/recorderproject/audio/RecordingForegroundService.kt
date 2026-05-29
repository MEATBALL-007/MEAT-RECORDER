package com.example.recorderproject.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.recorderproject.MainActivity
import com.example.recorderproject.R

/**
 * Q2: Foreground service that keeps the recording alive when the screen goes
 * off or the app is backgrounded.
 *
 * Does NOT do the recording itself — AudioRecorderManager (already running
 * inside MainActivity's ViewModel) keeps doing that. The service only exists
 * to (a) own the persistent microphone-typed foreground notification and
 * (b) raise the process priority so Android doesn't kill the recording loop.
 *
 * Lifecycle:
 *  - ViewModel.startRecording() → context.startForegroundService(intent)
 *  - service immediately calls startForeground(NOTIF_ID, notification, MIC type)
 *  - ViewModel.stopRecording() → context.stopService(intent)
 */
class RecordingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notification = buildNotification(this, isRecording = true)
        val typeFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, typeFlags)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "meatrec_recording"
        const val NOTIF_ID = 4011
        const val ACTION_STOP_RECORDING = "com.example.recorderproject.ACTION_STOP_RECORDING"

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID, "Recording",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Live recording session"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(ch)
                }
            }
        }

        private fun buildNotification(ctx: Context, isRecording: Boolean): Notification {
            val openAppIntent = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                ctx, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val stopIntent = Intent(ACTION_STOP_RECORDING).setPackage(ctx.packageName)
            val stopPi = PendingIntent.getBroadcast(
                ctx, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle("MEAT REC")
                .setContentText(if (isRecording) "Recording in progress — tap to return" else "Idle")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setColor(0xFFFA4616.toInt())
                .setColorized(true)
                .setOngoing(isRecording)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }
    }
}
