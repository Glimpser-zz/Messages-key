package org.fossify.messages.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.MainActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.helpers.SpamReminderScheduler

class SpamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (!appContext.config.spamReminderEnabled) return

        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                val spamSenders = appContext.config.spamConversations
                val personalSenders = appContext.config.personalConversations
                val unreadCount = appContext.conversationsDB.getNonArchived()
                    .filter { spamSenders.contains(it.phoneNumber) && !personalSenders.contains(it.phoneNumber) }
                    .sumBy { it.unreadCount }

                if (unreadCount > 0) {
                    showReminder(appContext, unreadCount)
                }
                SpamReminderScheduler.scheduleNext(appContext)
            } catch (_: Exception) {
                SpamReminderScheduler.scheduleNext(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReminder(context: Context, unreadCount: Int) {
        val channelId = "spam_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.spam_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openSpamIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SPAM_TAB, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            53171,
            openSpamIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentTitle(context.getString(R.string.spam_reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.spam_reminder_message, unreadCount, unreadCount))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(53172, notification)
        } catch (_: SecurityException) {
            // Notifications may be disabled by the user or OS.
        }
    }
}
