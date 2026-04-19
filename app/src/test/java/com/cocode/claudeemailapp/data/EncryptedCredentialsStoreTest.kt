package com.cocode.claudeemailapp.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedCredentialsStoreTest {

    private lateinit var store: EncryptedCredentialsStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = EncryptedCredentialsStore(prefs)
    }

    private val sample = MailCredentials(
        displayName = "Display",
        emailAddress = "me@ex.com",
        password = "pw",
        imapHost = "imap.ex",
        imapPort = 1993,
        smtpHost = "smtp.ex",
        smtpPort = 2465,
        smtpUseStartTls = true,
        serviceAddress = "svc@ex",
        sharedSecret = "secret"
    )

    @Test
    fun hasCredentials_initiallyFalse() {
        assertFalse(store.hasCredentials())
    }

    @Test
    fun load_initiallyReturnsNull() {
        assertNull(store.load())
    }

    @Test
    fun saveThenLoad_returnsRoundTripEquivalent() {
        store.save(sample)
        assertTrue(store.hasCredentials())
        assertEquals(sample, store.load())
    }

    @Test
    fun clear_removesAllFields() {
        store.save(sample)
        store.clear()
        assertFalse(store.hasCredentials())
        assertNull(store.load())
    }

    @Test
    fun save_overwritesPriorValues() {
        store.save(sample)
        val next = sample.copy(emailAddress = "other@ex.com", imapPort = 2222)
        store.save(next)
        assertEquals(next, store.load())
    }

}
