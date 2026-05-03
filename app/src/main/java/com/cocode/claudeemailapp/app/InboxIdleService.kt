package com.cocode.claudeemailapp.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.cocode.claudeemailapp.MainActivity
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.InboxNotificationPrefs
import com.cocode.claudeemailapp.mail.ImapIdleListener
import com.cocode.claudeemailapp.mail.ImapMailFetcher
import com.cocode.claudeemailapp.mail.JakartaImapIdleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground-service host for the IMAP IDLE listener. Foreground services are
 * exempt from Android's App Freezer, so the IDLE socket stays alive while the
 * Activity is backgrounded — closing the gap that v0's foreground-only poll
 * couldn't cover.
 *
 * The persistent notification is the price (Android 8+ requirement). Disabling
 * notifications for the channel hides the icon but keeps the service running.
 */
class InboxIdleService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        startListener()
    }

    // START_STICKY so the OS recreates us after a low-memory kill. Without this
    // the IDLE listener stays dead until the user reopens MainActivity.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startInForeground() {
        ensureChannel()
        val notif = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startListener() {
        val credentials = CredentialsStore(this).load()
        if (credentials == null) {
            stopSelf()
            return
        }
        val prefs = InboxNotificationPrefs(this)
        val notifier = InboxNotifier(
            context = this,
            prefs = prefs,
            isForeground = {
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        )
        val fetcher = ImapMailFetcher()
        val listener = ImapIdleListener(
            sessionFactory = { JakartaImapIdleSession(credentials) },
            onActivity = {
                runCatching { fetcher.fetchRecent(credentials, FETCH_BATCH) }
                    .getOrNull()
                    ?.firstOrNull()
                    ?.let { notifier.handle(it) }
            }
        )
        listenerJob = scope.launch { listener.run() }
    }

    override fun onDestroy() {
        listenerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
                .apply { description = CHANNEL_DESC; setShowBadge(false) }
        )
    }

    private fun buildOngoingNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Watching inbox")
            .setContentText("Listening for replies in the background")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "inbox_watcher"
        private const val CHANNEL_NAME = "Inbox watcher"
        private const val CHANNEL_DESC =
            "Keeps an IMAP IDLE connection open so reply notifications work in the background."
        private const val NOTIF_ID = 4_201
        private const val FETCH_BATCH = 1

        fun start(context: Context) {
            val intent = Intent(context, InboxIdleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, InboxIdleService::class.java))
        }
    }
}
