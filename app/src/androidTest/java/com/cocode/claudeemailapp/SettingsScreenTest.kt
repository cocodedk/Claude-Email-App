package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.SettingsScreen
import com.cocode.claudeemailapp.data.MailCredentials
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun creds(
        displayName: String = "Display",
        serviceAddress: String = "svc@ex",
        sharedSecret: String = "secret",
        startTls: Boolean = false
    ) = MailCredentials(
        displayName = displayName,
        emailAddress = "me@ex",
        password = "pw",
        imapHost = "imap.ex",
        imapPort = 993,
        smtpHost = "smtp.ex",
        smtpPort = 465,
        smtpUseStartTls = startTls,
        serviceAddress = serviceAddress,
        sharedSecret = sharedSecret
    )

    @Test
    fun showsSavedCredentialDetails() {
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(),
                onBack = {},
                onSignOut = {},
                onEdit = {}
            )
        }
        composeRule.onNodeWithText("Display").assertIsDisplayed()
        composeRule.onNodeWithText("me@ex").assertIsDisplayed()
        composeRule.onNodeWithText("imap.ex").assertIsDisplayed()
        composeRule.onNodeWithText("svc@ex").assertIsDisplayed()
    }

    @Test
    fun emptyDisplayName_showsNotSet() {
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(displayName = ""),
                onBack = {},
                onSignOut = {},
                onEdit = {}
            )
        }
        composeRule.onNodeWithText("(not set)").assertIsDisplayed()
    }

    @Test
    fun emptySharedSecret_showsNotSet() {
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(sharedSecret = "", serviceAddress = ""),
                onBack = {},
                onSignOut = {},
                onEdit = {}
            )
        }
        // both serviceAddress and sharedSecret empty → two "(not set)" entries
    }

    @Test
    fun sharedSecretSet_showsMasked() {
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(sharedSecret = "secret-value"),
                onBack = {},
                onSignOut = {},
                onEdit = {}
            )
        }
        composeRule.onNodeWithText("••••••").assertIsDisplayed()
    }

    @Test
    fun startTls_showsStartTls() {
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(startTls = true),
                onBack = {},
                onSignOut = {},
                onEdit = {}
            )
        }
        composeRule.onNodeWithText("STARTTLS").assertIsDisplayed()
    }

    @Test
    fun backButton_callsCallback() {
        var backed = false
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(),
                onBack = { backed = true },
                onSignOut = {},
                onEdit = {}
            )
        }
        composeRule.onNodeWithTag("settings_back").performClick()
        assert(backed)
    }

    @Test
    fun editButton_callsCallback() {
        var edited = false
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(),
                onBack = {},
                onSignOut = {},
                onEdit = { edited = true }
            )
        }
        composeRule.onNodeWithTag("settings_edit").performClick()
        assert(edited)
    }

    @Test
    fun signOutButton_callsCallback() {
        var signedOut = false
        composeRule.setContent {
            SettingsScreen(
                credentials = creds(),
                onBack = {},
                onSignOut = { signedOut = true },
                onEdit = {}
            )
        }
        composeRule.onNodeWithTag("settings_signout").performClick()
        assert(signedOut)
    }
}
