package org.fossify.messages.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.fossify.messages.receivers.SpamReminderReceiver

object SpamReminderScheduler {
    private const val REQUEST_CODE = 53170

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        if (!appContext.config.spamReminderEnabled) {
            cancel(appContext)
            return
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderPendingIntent(appContext)
        val intervalMillis = appContext.config.spamReminderIntervalHours * 60L * 60L * 1000L
        val triggerAt = System.currentTimeMillis() + intervalMillis
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(context.applicationContext))
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SpamReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
