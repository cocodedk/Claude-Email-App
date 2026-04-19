package com.cocode.claudeemailapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MailCredentialsTest {

    private val sample = MailCredentials(
        displayName = "d",
        emailAddress = "e",
        password = "p",
        imapHost = "ih",
        imapPort = 1,
        smtpHost = "sh",
        smtpPort = 2,
        smtpUseStartTls = true,
        serviceAddress = "sv",
        sharedSecret = "ss"
    )

    @Test
    fun copy_preservesAllFieldsWhenUnchanged() {
        assertEquals(sample, sample.copy())
    }

    @Test
    fun copy_withChangedField_producesInequality() {
        assertNotEquals(sample, sample.copy(emailAddress = "other"))
    }

    @Test
    fun equals_hashCode_areValueBased() {
        val a = sample.copy()
        val b = sample.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
