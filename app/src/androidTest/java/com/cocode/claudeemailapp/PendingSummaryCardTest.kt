package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.PendingSummary
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The longest status label ("waiting for the service to confirm") must not squeeze the command preview. */
@RunWith(AndroidJUnit4::class)
class PendingSummaryCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun command() = PendingCommand(
        messageId = "<m1>",
        sentAt = 0L,
        to = "svc@ex",
        subject = "s",
        kind = "command",
        bodyPreview = "Run the full test suite and fix whatever fails in the mail module",
        taskId = 4242L,
        status = PendingStatus.AWAITING_ACK
    )

    private fun render(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ClaudeEmailAppTheme {
                    // 360 dp is the narrowest common phone width.
                    Box(Modifier.width(360.dp)) { PendingSummary(pending = listOf(command())) }
                }
            }
        }
    }

    @Test
    fun preview_keepsMostOfTheRow_atNormalFontSize() {
        render(fontScale = 1f)
        composeRule.onNodeWithTag("pending_preview_<m1>").assertIsDisplayed().assertWidthIsAtLeast(250.dp)
    }

    @Test
    fun preview_keepsMostOfTheRow_atTwiceTheFontSize() {
        render(fontScale = 2f)
        composeRule.onNodeWithTag("pending_preview_<m1>").assertIsDisplayed().assertWidthIsAtLeast(250.dp)
    }
}
