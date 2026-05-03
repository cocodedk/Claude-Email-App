package com.cocode.claudeemailapp.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-scoped foreground signal. The lifecycle observer runs on the main
 * thread (where AndroidX Lifecycle requires it); the AtomicBoolean read is
 * safe from any thread, so InboxNotifier callers on Dispatchers.IO don't have
 * to hop to Main just to ask "are we visible?".
 *
 * Replaces direct `ProcessLifecycleOwner.get().lifecycle.currentState` reads
 * from background threads, which work in practice but violate the documented
 * main-thread contract on LifecycleRegistry.
 */
object ForegroundState {

    private val foreground = AtomicBoolean(false)

    /** Wire once from Application.onCreate; must run on the main thread. */
    fun bind() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground.set(true) }
            override fun onStop(owner: LifecycleOwner) { foreground.set(false) }
        })
    }

    fun isForeground(): Boolean = foreground.get()
}
