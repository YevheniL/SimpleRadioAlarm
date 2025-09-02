package com.backon.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.core.app.NotificationCompat

class RadioPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val streamUrl = "https://online.radioroks.ua/RadioROKS_HD"
    private val notificationId = 1
    private val channelId = "RadioAlarmChannel"

    companion object {
        const val ACTION_STOP = "com.example.radioalarm.ACTION_STOP"
        const val ACTION_PLAYBACK_STOPPED = "com.example.radioalarm.ACTION_PLAYBACK_STOPPED"
        const val ACTION_PLAYBACK_STARTED = "com.example.radioalarm.ACTION_PLAYBACK_STARTED"
        var isServiceRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(
            notificationId,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        startPlayback()
        isServiceRunning = true
        return START_STICKY
    }

    private fun startPlayback() {
        if (mediaPlayer != null) return // Already playing or preparing
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            try {
                val intent = Intent(ACTION_PLAYBACK_STARTED)
                sendBroadcast(intent)
                setDataSource(streamUrl)
                setOnPreparedListener { player ->
                    Log.d("RadioPlayerService", "Playback started.")
                    player.start()
                    AlarmReceiver.releaseWakeLock()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isServiceRunning = false
        AlarmReceiver.releaseWakeLock()
        Log.d("RadioPlayerService", "Playback stopped.")
        // Send a broadcast message to inform the UI
        val intent = Intent(ACTION_PLAYBACK_STOPPED)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            channelId,
            "Radio Alarm Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        // Intent to open the app when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        // Intent for the "Stop" button in the notification
        val stopIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Radio ROKS Alarm")
            .setContentText("Playing live radio...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your icon
            .setContentIntent(pendingIntent) // Set the intent to open the app
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}