package com.cocode.claudeemailapp.app

import android.app.Application

// Named *Application* (not *App*) to avoid clashing with the ClaudeEmailApp Composable in AppRoot.
class ClaudeEmailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        InboxNotifier.registerChannel(this)
    }
}
