package com.noxzshqwrt.calendario

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "reminder_channel"
private const val REMINDER_WORK = "daily_reminder"
private const val NOTIFICATION_ID = 1001
private const val REMINDER_HOUR = 21

fun initReminder(activity: Activity) {
    if (Build.VERSION.SDK_INT >= 33 &&
        activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }
    ReminderScheduler.schedule(activity)
}

object ReminderScheduler {
    fun schedule(ctx: Context) {
        ensureChannel(ctx)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(REMINDER_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMs = Duration.between(now, next).toMillis()

        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(REMINDER_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Lembrete diário",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Lembrete para registrar seu dia"
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        ReminderScheduler.ensureChannel(applicationContext)
        showNotification(applicationContext)
        ReminderScheduler.schedule(applicationContext)
        return Result.success()
    }

    private fun showNotification(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        val pi = PendingIntent.getActivity(
            ctx, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val texts = listOf(
            "Marcou hoje?",
            "Como foi seu dia?",
            "Já registrou seu dia?",
            "Não esquece de marcar"
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("Calendário")
            .setContentText(texts.random())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notif)
    }
}
