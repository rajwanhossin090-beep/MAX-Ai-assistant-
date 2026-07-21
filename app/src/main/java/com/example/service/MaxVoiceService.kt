package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.live.LiveSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MaxVoiceService : Service() {
    companion object {
        private const val TAG = "MaxVoiceService"
        private const val CHANNEL_ID = "max_voice_assistant_channel"
        private const val NOTIF_ID = 1001

        const val ACTION_START = "ACTION_START_MAX_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_MAX_SERVICE"

        var instance: MaxVoiceService? = null
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val binder = LocalBinder()

    var liveSessionManager: LiveSessionManager? = null
        private set

    inner class LocalBinder : Binder() {
        fun getService(): MaxVoiceService = this@MaxVoiceService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MaxVoiceService created")
        createNotificationChannel()

        liveSessionManager = LiveSessionManager(applicationContext, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                liveSessionManager?.stopSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundServiceWithNotification()
                liveSessionManager?.startSession()
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MaxVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAX Voice Assistant")
            .setContentText("MAX is active & ready in the background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.mipmap.ic_launcher, "Stop MAX", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException starting foreground service with mic type, falling back", e)
                try {
                    startForeground(NOTIF_ID, notification)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error starting basic foreground service", e2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service type mic", e)
                try {
                    startForeground(NOTIF_ID, notification)
                } catch (e2: Exception) {
                    Log.e(TAG, "Error starting basic foreground service", e2)
                }
            }
        } else {
            try {
                startForeground(NOTIF_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAX Foreground Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for MAX voice background service"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        liveSessionManager?.stopSession()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
        Log.d(TAG, "MaxVoiceService destroyed")
    }
}
