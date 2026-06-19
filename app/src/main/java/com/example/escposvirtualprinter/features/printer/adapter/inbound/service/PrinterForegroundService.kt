package com.example.escposvirtualprinter.features.printer.adapter.inbound.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.escposvirtualprinter.app.AppGraph
import com.example.escposvirtualprinter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PrinterForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    AppGraph.printerUseCase.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> {
                val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
                startForeground(NOTIFICATION_ID, notification(port))
                scope.launch { AppGraph.printerUseCase.start(port) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch { AppGraph.printerUseCase.stop() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(port: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("TCP server listening on port $port")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.printer_service_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "printer_server"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_PORT = 9100
        private const val EXTRA_PORT = "port"
        private const val ACTION_STOP = "com.example.escposvirtualprinter.STOP"

        fun startIntent(context: Context, port: Int): Intent {
            return Intent(context, PrinterForegroundService::class.java).putExtra(EXTRA_PORT, port)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, PrinterForegroundService::class.java).setAction(ACTION_STOP)
        }
    }
}
