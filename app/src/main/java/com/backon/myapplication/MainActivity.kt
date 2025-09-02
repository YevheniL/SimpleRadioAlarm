package com.backon.myapplication

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.util.Calendar
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var setAlarmButton: Button
    private lateinit var alarmStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity)

        timePicker = findViewById(R.id.timePicker)
        setAlarmButton = findViewById(R.id.setAlarmButton)
        alarmStatusTextView = findViewById(R.id.alarmStatusTextView)

        setAlarmButton.setOnClickListener {
            checkAndSetAlarm()
        }
    }

    private fun checkAndSetAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Guide user to settings to grant permission
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                startActivity(intent)
                Toast.makeText(this, "Please grant permission to schedule exact alarms.", Toast.LENGTH_LONG).show()
            }
        } else {
            setAlarm()
        }
    }

    private fun setAlarm() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
        calendar.set(Calendar.MINUTE, timePicker.minute)
        calendar.set(Calendar.SECOND, 0)

        // If the time is in the past, set it for the next day
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DATE, 1)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        val timeString = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
        alarmStatusTextView.text = "Alarm set for $timeString"
        Toast.makeText(this, "Alarm set for $timeString", Toast.LENGTH_SHORT).show()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Start the service to play the radio
        Log.d("AlarmReceiver","onReceive")
        val serviceIntent = Intent(context, RadioPlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

class RadioPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val streamUrl = "http://online.radioroks.ua/RadioROKS"
    private val channelId = "RadioAlarmChannel"
    private val notificationId = 1

    companion object {
        const val ACTION_STOP = "com.example.radioalarm.ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        Log.d("onStartCommand","onStartCommand")
        startForeground(notificationId, createNotification())
        startPlayback()

        return START_STICKY
    }

    private fun startPlayback() {
        Log.d("onStartCommand","startPlayback")

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(streamUrl)
                setOnPreparedListener { it.start() }
                prepareAsync() // Prepare async to not block the main thread
                Log.d("onStartCommand","prepareAsync")

            } catch (e: IOException) {
                e.printStackTrace()
                stopSelf() // Stop service if URL is invalid
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Radio Alarm Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, RadioPlayerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Radio ROKS")
            .setContentText("Playing live...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a proper icon
            .addAction(R.drawable.ic_launcher_background, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
