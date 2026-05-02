package com.cocode.claudeemailapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cocode.claudeemailapp.MainActivity
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.mail.FetchedMessage
import com.cocode.claudeemailapp.protocol.Kinds

/**
 * Posts a system notification when a new agent reply lands. Skips ACK kind
 * (just-sent confirmation, would be noise), suppresses while the app is in
 * foreground (you're already looking at it), and fails open on null/unknown
 * envelope kinds so future schema additions aren't silently swallowed.
 */
class InboxNotifier(
    private val context: Context,
    private val prefs: InboxNotificationPrefs,
    private val isForeground: () -> Boolean
) {

    fun handle(message: FetchedMessage) {
        if (!shouldPost(message, prefs.notificationsEnabled.value, isForeground())) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val id = (message.messageId.takeIf { it.isNotBlank() } ?: message.subject).hashCode()
        val tap = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = message.fromName?.takeIf { it.isNotBlank() } ?: message.from
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            nm.notify(id, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied at the OS level — silently skip.
        }
    }

    companion object {
        const val CHANNEL_ID = "replies"
        private const val CHANNEL_NAME = "Replies"
        private const val CHANNEL_DESC =
            "Notifications when an agent replies to one of your commands."

        fun registerChannel(context: Context) {
            // Notification channels are an Oreo+ concept. On API 24/25 the system
            // routes notifications without a channel; nothing to register.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = CHANNEL_DESC }
            )
        }

        fun shouldPost(message: FetchedMessage, enabled: Boolean, isForeground: Boolean): Boolean {
            if (!enabled) return false
            if (isForeground) return false
            if (message.envelope?.kind == Kinds.ACK) return false
            return true
        }
    }
}
