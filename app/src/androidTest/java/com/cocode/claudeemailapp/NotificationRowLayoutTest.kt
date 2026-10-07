package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.SettingsScreen
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings, notification row on a 360 dp screen: the text takes the row's remaining width and never
 * runs under the switch, and the switch keeps its own width.
 */
@RunWith(AndroidJUnit4::class)
class NotificationRowLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderSettings(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ClaudeEmailAppTheme {
                    Box(Modifier.width(360.dp)) {
                        SettingsScreen(
                            credentials = MailCredentials("d", "me@ex", "pw", "imap.ex", 993, "smtp.ex", 465, false, "svc@ex", "s"),
                            syncIntervalMs = 60_000L, onSyncIntervalChange = {},
                            notificationsEnabled = true, onNotificationsEnabledChange = {},
                            onBack = {}, onSignOut = {}, onEdit = {}, onOpenDiagnostics = {}
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_notifications_toggle"))
    }

    private fun assertTextEndsBeforeSwitch(fontScale: Float) {
        renderSettings(fontScale)
        val text = composeRule.onNodeWithText("Show a notification on this phone", substring = true).getUnclippedBoundsInRoot()
        val toggle = composeRule.onNodeWithTag("settings_notifications_toggle").getUnclippedBoundsInRoot()
        assertTrue("text ends at ${text.right}, switch starts at ${toggle.left}", text.right <= toggle.left)
    }

    private fun assertSwitchKeepsItsWidth(fontScale: Float) {
        renderSettings(fontScale)
        val toggle = composeRule.onNodeWithTag("settings_notifications_toggle").getUnclippedBoundsInRoot()
        assertTrue("switch is ${toggle.right - toggle.left} wide", toggle.right - toggle.left >= 48.dp)
    }

    @Test fun text_endsBeforeTheSwitch_atNormalText() = assertTextEndsBeforeSwitch(1f)
    @Test fun text_endsBeforeTheSwitch_at1_3xText() = assertTextEndsBeforeSwitch(1.3f)
    @Test fun text_endsBeforeTheSwitch_at2xText() = assertTextEndsBeforeSwitch(2f)

    @Test fun switch_keepsItsWidth_atNormalText() = assertSwitchKeepsItsWidth(1f)
    @Test fun switch_keepsItsWidth_at1_3xText() = assertSwitchKeepsItsWidth(1.3f)
    @Test fun switch_keepsItsWidth_at2xText() = assertSwitchKeepsItsWidth(2f)
}
