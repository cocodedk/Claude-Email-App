package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.EnvelopeErrorBanner
import com.cocode.claudeemailapp.app.PendingSummary
import com.cocode.claudeemailapp.app.SettingsScreen
import com.cocode.claudeemailapp.app.ProjectStatePills
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import com.cocode.claudeemailapp.data.MailCredentials
import com.cocode.claudeemailapp.data.PendingCommand
import com.cocode.claudeemailapp.data.PendingStatus
import com.cocode.claudeemailapp.data.ProjectSummary
import com.cocode.claudeemailapp.protocol.AgentStatusValues
import com.cocode.claudeemailapp.protocol.EnvelopeError
import com.cocode.claudeemailapp.protocol.ErrorCodes
import com.cocode.claudeemailapp.protocol.TaskStateValues
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rows that put a status chip or a button label next to other content must wrap, not push the
 * rest out of the row, on the narrowest common phone (360 dp) at normal, 1.3x and 2x text.
 */
@RunWith(AndroidJUnit4::class)
class NarrowScreenLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val rowWidth = 296.dp // a card inside a list on a 360 dp screen

    private fun render(fontScale: Float, width: Dp = rowWidth, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ClaudeEmailAppTheme { Box(Modifier.width(width).testTag("box")) { content() } }
            }
        }
    }

    private fun SemanticsNodeInteraction.assertInsideBox() {
        val box = composeRule.onNodeWithTag("box").getUnclippedBoundsInRoot()
        val b = getUnclippedBoundsInRoot()
        assertTrue("left ${b.left} < ${box.left}", b.left >= box.left - 0.5.dp)
        assertTrue("right ${b.right} > ${box.right}", b.right <= box.right + 0.5.dp)
    }

    // --- Projects: agent pill + task pill ------------------------------------------------------

    private fun project(agent: String?, task: String?, running: Long?, queue: Int = 0) =
        ProjectSummary(name = "p", path = "/p", runningTaskId = running, queueDepth = queue, agentStatus = agent, taskState = task)

    /** Every agent state and task state, with the number of pills each should show. */
    private val projectCases = listOf(
        project(AgentStatusValues.STALE, TaskStateValues.WORKING, 12) to 2,
        project(AgentStatusValues.ONLINE, TaskStateValues.WAITING, 12) to 2,
        project(AgentStatusValues.OFFLINE, TaskStateValues.COMPLETED, 12) to 2,
        project("brand-new-status", TaskStateValues.ERROR, 12) to 2,
        project(AgentStatusValues.STALE, "paused", 12) to 2,
        project(AgentStatusValues.STALE, null, 12) to 2,
        project(AgentStatusValues.STALE, null, null, queue = 3) to 2,
        project(AgentStatusValues.STALE, null, null) to 2,
        project(null, TaskStateValues.WORKING, 12) to 1
    )

    private fun checkProjectPills(fontScale: Float) {
        render(fontScale) {
            Column {
                projectCases.forEachIndexed { i, (p, _) ->
                    Box(Modifier.width(rowWidth).testTag("pills_$i")) { ProjectStatePills(p) }
                }
            }
        }
        projectCases.forEachIndexed { i, (_, pills) ->
            val box = composeRule.onNodeWithTag("pills_$i").getUnclippedBoundsInRoot()
            val nodes = composeRule.onAllNodes(
                hasAnyAncestor(hasTestTag("pills_$i")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
            ).fetchSemanticsNodes()
            assertEquals("pills for case $i", pills, nodes.size)
            nodes.forEach { n ->
                val right = with(composeRule.density) { n.boundsInRoot.right.toDp() }
                val width = with(composeRule.density) { n.boundsInRoot.width.toDp() }
                assertTrue("case $i: pill ends at $right, row ends at ${box.right}", right <= box.right + 0.5.dp)
                assertTrue("case $i: pill text has no room ($width)", width >= 12.dp)
            }
        }
    }

    @Test fun projectPills_fit_atNormalText() = checkProjectPills(1f)
    @Test fun projectPills_fit_at1_3xText() = checkProjectPills(1.3f)
    @Test fun projectPills_fit_at2xText() = checkProjectPills(2f)

    // --- Pending commands: the two action buttons ----------------------------------------------

    private fun pendingList() = listOf(
        PendingCommand("<f>", 0L, "svc@ex", "s", "command", "failed one", status = PendingStatus.FAILED),
        PendingCommand("<r>", 0L, "svc@ex", "s", "command", "running one", taskId = 5L, status = PendingStatus.RUNNING)
    )

    private fun checkPendingButtons(fontScale: Float) {
        render(fontScale) { PendingSummary(pending = pendingList()) }
        composeRule.onNodeWithTag("pending_retry_<f>").assertIsDisplayed().assertInsideBox()
        composeRule.onNodeWithTag("pending_cancel_<r>").assertIsDisplayed().assertInsideBox()
        composeRule.onNodeWithText("Send saved preview again").assertInsideBox()
        composeRule.onNodeWithText("Cancel project task").assertInsideBox()
    }

    @Test fun pendingButtons_fit_atNormalText() = checkPendingButtons(1f)
    @Test fun pendingButtons_fit_at1_3xText() = checkPendingButtons(1.3f)
    @Test fun pendingButtons_fit_at2xText() = checkPendingButtons(2f)

    // --- Error banner: the action button next to "Diagnostics" ---------------------------------

    private fun checkBannerButtons(fontScale: Float) {
        render(fontScale) {
            EnvelopeErrorBanner(error = EnvelopeError(ErrorCodes.INTERNAL, "oops", retryable = true))
        }
        composeRule.onNodeWithTag("envelope_error_retry").assertIsDisplayed().assertInsideBox()
        composeRule.onNodeWithTag("envelope_error_diagnostics").assertIsDisplayed().assertInsideBox()
    }

    @Test fun bannerButtons_fit_atNormalText() = checkBannerButtons(1f)
    @Test fun bannerButtons_fit_at1_3xText() = checkBannerButtons(1.3f)
    @Test fun bannerButtons_fit_at2xText() = checkBannerButtons(2f)

    // --- Steering bar: Request status, Cancel project task, More -------------------------------

    private fun checkSteeringChips(fontScale: Float) {
        render(fontScale, width = 360.dp) {
            SteeringBar(
                state = SteeringBarState.Idle,
                controller = SteeringBarController(CoroutineScope(Dispatchers.Main))
            )
        }
        for (tag in listOf("steering_chip_status", "steering_chip_cancel", "steering_chip_more")) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed().assertInsideBox()
        }
    }

    @Test fun steeringChips_fit_atNormalText() = checkSteeringChips(1f)
    @Test fun steeringChips_fit_at1_3xText() = checkSteeringChips(1.3f)
    @Test fun steeringChips_fit_at2xText() = checkSteeringChips(2f)

    // --- Settings: the notification text beside its switch -------------------------------------

    private fun checkNotificationRow(fontScale: Float) {
        render(fontScale, width = 360.dp) {
            SettingsScreen(
                credentials = MailCredentials("d", "me@ex", "pw", "imap.ex", 993, "smtp.ex", 465, false, "svc@ex", "s"),
                syncIntervalMs = 60_000L, onSyncIntervalChange = {},
                notificationsEnabled = true, onNotificationsEnabledChange = {},
                onBack = {}, onSignOut = {}, onEdit = {}, onOpenDiagnostics = {}
            )
        }
        composeRule.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("settings_notifications_toggle"))
        val text = composeRule.onNodeWithText("Show a notification on this phone", substring = true).getUnclippedBoundsInRoot()
        val toggle = composeRule.onNodeWithTag("settings_notifications_toggle").getUnclippedBoundsInRoot()
        assertTrue("text ends at ${text.right}, switch starts at ${toggle.left}", text.right <= toggle.left)
        // The switch keeps its own width; the text must not squeeze it to nothing at the row's edge.
        assertTrue("switch is ${toggle.right - toggle.left} wide", toggle.right - toggle.left >= 48.dp)
    }

    @Test fun notificationText_clearsTheSwitch_atNormalText() = checkNotificationRow(1f)
    @Test fun notificationText_clearsTheSwitch_at1_3xText() = checkNotificationRow(1.3f)
    @Test fun notificationText_clearsTheSwitch_at2xText() = checkNotificationRow(2f)
}
