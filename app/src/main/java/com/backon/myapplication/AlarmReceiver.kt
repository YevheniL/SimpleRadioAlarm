package com.backon.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private var wakeLock: PowerManager.WakeLock? = null
        fun acquireWakeLock(context: Context) {
            wakeLock?.release()
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioAlarm::WakeLockTag")
                    .apply {
                        acquire(10 * 60 * 1000L /* 10 minutes */)
                    }
        }

        fun releaseWakeLock() {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        acquireWakeLock(context)
        Log.d("AlarmReceiver", "Alarm triggered, starting service.")
        val serviceIntent = Intent(context, RadioPlayerService::class.java)
        context.startForegroundService(serviceIntent)
    }
}