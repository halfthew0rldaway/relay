package dev.bleu.locallink.data.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TransferService : Service() {
    companion object {
        private const val CHANNEL_ID = "locallink_transfer"
        private const val NOTIFICATION_ID = 2001
        
        var isRunning = false

        fun start(context: Context, fileName: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                putExtra("fileName", fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isRunning = true
        }
        
        fun stop(context: Context) {
            if (isRunning) {
                context.stopService(Intent(context, TransferService::class.java))
                isRunning = false
            }
        }
        
        fun updateProgress(context: Context, fileName: String, progress: Int, isDone: Boolean) {
            if (!isRunning) return
            val intent = Intent(context, TransferService::class.java).apply {
                putExtra("update", true)
                putExtra("fileName", fileName)
                putExtra("progress", progress)
                putExtra("isDone", isDone)
            }
            context.startService(intent) // Akan diteruskan ke onStartCommand untuk update notifikasi
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "File Transfer", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        val isUpdate = intent?.getBooleanExtra("update", false) ?: false
        val fileName = intent?.getStringExtra("fileName") ?: "File"
        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val isDone = intent?.getBooleanExtra("isDone", false) ?: false
        
        val builder = NotificationCompat.Builder(this, "locallink_transfer")
            .setContentTitle(if (isDone) "Transfer Complete" else "Transferring $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!isDone)
            
        if (!isDone) {
            builder.setProgress(100, progress, progress == 0)
        } else {
            builder.setContentText("Successfully transferred $fileName")
        }

        startForeground(NOTIFICATION_ID, builder.build())
        
        if (isDone) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            isRunning = false
        }
        
        return START_NOT_STICKY
    }
}
