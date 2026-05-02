package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefillCredentialsTest {

    private fun mapGetter(extras: Map<String, String?>): (String) -> String? = { extras[it] }

    @Test
    fun `returns null when email is missing`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertNull(result)
    }

    @Test
    fun `returns null when password is missing`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertNull(result)
    }

    @Test
    fun `returns null when imap host is missing`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertNull(result)
    }

    @Test
    fun `returns null when smtp host is missing`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com"
        )))
        assertNull(result)
    }

    @Test
    fun `treats blank required field as missing`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "   ",
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertNull(result)
    }

    @Test
    fun `defaults ports and starttls when omitted`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertEquals(993, result?.imapPort)
        assertEquals(465, result?.smtpPort)
        assertEquals(false, result?.smtpUseStartTls)
        assertEquals("", result?.displayName)
        assertEquals("", result?.serviceAddress)
        assertEquals("", result?.sharedSecret)
    }

    @Test
    fun `parses every field when fully populated`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_DISPLAY_NAME to "Test User",
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_PASSWORD to "f^kqB5%T8TAk!zK7NBVAKq^\$r",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_IMAP_PORT to "143",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com",
            PrefillCredentials.EXTRA_SMTP_PORT to "587",
            PrefillCredentials.EXTRA_SMTP_STARTTLS to "TRUE",
            PrefillCredentials.EXTRA_SERVICE_ADDRESS to "claude@example.com",
            PrefillCredentials.EXTRA_SHARED_SECRET to "secret"
        )))
        assertEquals("Test User", result?.displayName)
        assertEquals("u@example.com", result?.emailAddress)
        assertEquals("f^kqB5%T8TAk!zK7NBVAKq^\$r", result?.password)
        assertEquals("imap.example.com", result?.imapHost)
        assertEquals(143, result?.imapPort)
        assertEquals("smtp.example.com", result?.smtpHost)
        assertEquals(587, result?.smtpPort)
        assertTrue(result?.smtpUseStartTls == true)
        assertEquals("claude@example.com", result?.serviceAddress)
        assertEquals("secret", result?.sharedSecret)
    }

    @Test
    fun `falls back to default port when value is non-numeric`() {
        val result = PrefillCredentials.fromExtras(mapGetter(mapOf(
            PrefillCredentials.EXTRA_EMAIL to "u@example.com",
            PrefillCredentials.EXTRA_PASSWORD to "p",
            PrefillCredentials.EXTRA_IMAP_HOST to "imap.example.com",
            PrefillCredentials.EXTRA_IMAP_PORT to "not-a-number",
            PrefillCredentials.EXTRA_SMTP_HOST to "smtp.example.com"
        )))
        assertEquals(993, result?.imapPort)
    }
}
