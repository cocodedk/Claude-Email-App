package com.cocode.claudeemailapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InboxNotificationPrefsTest {

    private lateinit var prefs: InboxNotificationPrefs

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteSharedPreferences(InboxNotificationPrefs.PREFS_NAME)
        prefs = InboxNotificationPrefs(ctx)
    }

    @Test
    fun `enabled by default`() {
        assertTrue(prefs.notificationsEnabled.value)
    }

    @Test
    fun `toggle persists across instances`() {
        prefs.setNotificationsEnabled(false)
        assertFalse(prefs.notificationsEnabled.value)

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val reread = InboxNotificationPrefs(ctx)
        assertFalse(reread.notificationsEnabled.value)

        reread.setNotificationsEnabled(true)
        assertTrue(reread.notificationsEnabled.value)
    }
}
