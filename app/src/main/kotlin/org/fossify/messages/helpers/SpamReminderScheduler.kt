package org.fossify.messages.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.fossify.messages.extensions.config
import org.fossify.messages.receivers.SpamReminderReceiver

object SpamReminderScheduler {
    private const val REQUEST_CODE = 53170
    private const val MINUTES_PER_HOUR = 60L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MILLIS_PER_SECOND = 1000L

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        if (!appContext.config.spamReminderEnabled) {
            cancel(appContext)
            return
        }

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderPendingIntent(appContext)
        val intervalMillis = appContext.config.spamReminderIntervalHours *
            MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
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
