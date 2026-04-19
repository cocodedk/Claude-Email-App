package com.cocode.claudeemailapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.data.CredentialsStore
import com.cocode.claudeemailapp.data.EncryptedCredentialsStore
import com.cocode.claudeemailapp.data.MailCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedCredentialsStoreInstrumentedTest {

    @Before
    fun resetPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences(EncryptedCredentialsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private val sample = MailCredentials(
        displayName = "Name",
        emailAddress = "user@example.com",
        password = "pw",
        imapHost = "imap.example.com",
        imapPort = 993,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUseStartTls = true,
        serviceAddress = "svc@example.com",
        sharedSecret = "secret-123"
    )

    @Test
    fun factoryCreatesRealEncryptedStore() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = CredentialsStore(ctx)
        assertTrue(store is EncryptedCredentialsStore)
    }

    @Test
    fun roundTrip_withRealEncryptedStore() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = CredentialsStore(ctx)
        store.clear()
        assertFalse(store.hasCredentials())
        store.save(sample)
        assertTrue(store.hasCredentials())
        assertEquals(sample, store.load())
        store.clear()
        assertNull(store.load())
    }
}
