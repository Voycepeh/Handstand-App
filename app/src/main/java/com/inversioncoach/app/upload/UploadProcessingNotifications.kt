package com.inversioncoach.app.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.inversioncoach.app.MainActivity
import com.inversioncoach.app.R

class UploadProcessingNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Uploaded video processing", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    fun running(jobId: String, title: String, detail: String, progress: Int?): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent(jobId))
            .setProgress(100, progress?.coerceIn(0, 100) ?: 0, progress == null)
            .build()
    }

    fun completed(jobId: String, sessionId: Long?) {
        ensureChannel()
        manager.notify(
            jobId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Uploaded video complete")
                .setContentText("Processing finished successfully.")
                .setAutoCancel(true)
                .setContentIntent(contentIntent(jobId, sessionId))
                .build(),
        )
    }

    fun failed(jobId: String, movedOn: Boolean, sessionId: Long?) {
        ensureChannel()
        manager.notify(
            jobId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Uploaded video failed")
                .setContentText(if (movedOn) "Failed after retries. Processing continued with next queued upload." else "Failed after retries.")
                .setAutoCancel(true)
                .setContentIntent(contentIntent(jobId, sessionId))
                .build(),
        )
    }

    private fun contentIntent(jobId: String, sessionId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("upload_job_id", jobId)
            sessionId?.let { putExtra("upload_session_id", it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "upload_processing"
    }
}
