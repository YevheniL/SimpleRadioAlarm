package com.backon.myapplication

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var setAlarmButton: Button
    private lateinit var playStopButton: Button
    private lateinit var alarmStatusTextView: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notifications permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val playbackStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RadioPlayerService.ACTION_PLAYBACK_STOPPED) {
                Log.d("MainActivity", "Received playback stopped broadcast. Updating UI.")
                updatePlayStopButton(false)
            } else if (intent?.action == RadioPlayerService.ACTION_PLAYBACK_STARTED) {
                updatePlayStopButton(true)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure this matches your layout file name, e.g., R.layout.activity_main
        setContentView(R.layout.activity)

        timePicker = findViewById(R.id.timePicker)
        setAlarmButton = findViewById(R.id.setAlarmButton)
        playStopButton = findViewById(R.id.playStopButton)
        alarmStatusTextView = findViewById(R.id.alarmStatusTextView)

        setAlarmButton.setOnClickListener {
            checkPermissionsAndSetAlarm()
        }

        playStopButton.setOnClickListener {
            togglePlayback()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update the button text in case the service was stopped from the notification
        val filter = IntentFilter(RadioPlayerService.ACTION_PLAYBACK_STOPPED)
        filter.addAction(RadioPlayerService.ACTION_PLAYBACK_STARTED)
        registerReceiver(playbackStoppedReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(playbackStoppedReceiver)
    }

    private fun updatePlayStopButton(boolean: Boolean) {
        if (boolean) {
            playStopButton.text = "Stop"
        } else {
            playStopButton.text = "Play"
        }
    }

    private fun togglePlayback() {
        val serviceIntent = Intent(this, RadioPlayerService::class.java)
        if (RadioPlayerService.isServiceRunning) {
            // Send an intent with the STOP action
            serviceIntent.action = RadioPlayerService.ACTION_STOP
            stopService(serviceIntent)
        } else {
            // Start the service to begin playback
            startForegroundService(serviceIntent)
        }
    }


    private fun checkPermissionsAndSetAlarm() {
        // Check for notification permission (required for Android 13+)
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return // Wait for user response
        }

        // Check for exact alarm permission (required for Android 12+)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Intent().also { intent ->
                intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                startActivity(intent)
                Toast.makeText(this, "Please grant permission to schedule exact alarms.", Toast.LENGTH_LONG).show()
                return
            }
        }
        setAlarm()
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun setAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, timePicker.hour)
            set(Calendar.MINUTE, timePicker.minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

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
