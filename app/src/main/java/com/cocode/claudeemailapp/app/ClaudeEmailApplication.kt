package com.cocode.claudeemailapp.app

import android.app.Application

/**
 * Application subclass: registers the "replies" notification channel once at
 * process start. Naming note: the existing Composable in [AppRoot] is named
 * `ClaudeEmailApp` — this class is `ClaudeEmailApplication` to avoid a clash.
 */
class ClaudeEmailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        InboxNotifier.registerChannel(this)
    }
}
