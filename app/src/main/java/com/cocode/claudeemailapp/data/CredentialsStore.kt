package com.cocode.claudeemailapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.AEADBadTagException

interface CredentialsStore {
    fun hasCredentials(): Boolean
    fun load(): MailCredentials?
    fun save(credentials: MailCredentials)
    fun clear()

    companion object {
        operator fun invoke(context: Context): CredentialsStore = EncryptedCredentialsStore.create(context)
    }
}

internal class EncryptedCredentialsStore(private val prefs: SharedPreferences) : CredentialsStore {

    override fun hasCredentials(): Boolean = prefs.contains(KEY_EMAIL)

    override fun load(): MailCredentials? {
        if (!hasCredentials()) return null
        return MailCredentials(
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            emailAddress = prefs.getString(KEY_EMAIL, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            imapHost = prefs.getString(KEY_IMAP_HOST, "").orEmpty(),
            imapPort = prefs.getInt(KEY_IMAP_PORT, 993),
            smtpHost = prefs.getString(KEY_SMTP_HOST, "").orEmpty(),
            smtpPort = prefs.getInt(KEY_SMTP_PORT, 465),
            smtpUseStartTls = prefs.getBoolean(KEY_SMTP_STARTTLS, false),
            serviceAddress = prefs.getString(KEY_SERVICE_ADDRESS, "").orEmpty(),
            sharedSecret = prefs.getString(KEY_SHARED_SECRET, "").orEmpty()
        )
    }

    override fun save(credentials: MailCredentials) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, credentials.displayName)
            .putString(KEY_EMAIL, credentials.emailAddress)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_IMAP_HOST, credentials.imapHost)
            .putInt(KEY_IMAP_PORT, credentials.imapPort)
            .putString(KEY_SMTP_HOST, credentials.smtpHost)
            .putInt(KEY_SMTP_PORT, credentials.smtpPort)
            .putBoolean(KEY_SMTP_STARTTLS, credentials.smtpUseStartTls)
            .putString(KEY_SERVICE_ADDRESS, credentials.serviceAddress)
            .putString(KEY_SHARED_SECRET, credentials.sharedSecret)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        internal const val PREFS_NAME = "mail_credentials"
        internal const val KEY_DISPLAY_NAME = "display_name"
        internal const val KEY_EMAIL = "email"
        internal const val KEY_PASSWORD = "password"
        internal const val KEY_IMAP_HOST = "imap_host"
        internal const val KEY_IMAP_PORT = "imap_port"
        internal const val KEY_SMTP_HOST = "smtp_host"
        internal const val KEY_SMTP_PORT = "smtp_port"
        internal const val KEY_SMTP_STARTTLS = "smtp_starttls"
        internal const val KEY_SERVICE_ADDRESS = "service_address"
        internal const val KEY_SHARED_SECRET = "shared_secret"

        fun create(context: Context): EncryptedCredentialsStore {
            return try {
                EncryptedCredentialsStore(openPrefs(context))
            } catch (t: Throwable) {
                if (!isKeystoreCorruption(t)) throw t
                // Master key rotated or persisted metadata can't be decrypted — wipe both
                // the prefs file and the keystore alias, then re-init from scratch. The
                // user will simply see the Setup screen again on next launch.
                context.deleteSharedPreferences(PREFS_NAME)
                runCatching {
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
                EncryptedCredentialsStore(openPrefs(context))
            }
        }

        private fun openPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun isKeystoreCorruption(t: Throwable): Boolean {
            var c: Throwable? = t
            while (c != null) {
                if (c is AEADBadTagException) return true
                c = c.cause
            }
            return false
        }
    }
}
