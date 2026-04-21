package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import com.cocode.claudeemailapp.app.steering.SteeringTemplateSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SteeringBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleState_showsStatusCancelMoreChips() {
        composeRule.setContent {
            SteeringBar(
                state = SteeringBarState.Idle,
                controller = SteeringBarController(CoroutineScope(Dispatchers.Main))
            )
        }
        composeRule.onNodeWithTag("steering_chip_status").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_chip_cancel").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_chip_more").assertIsDisplayed()
    }

    @Test
    fun awaitingUser_showsThreeTemplates() {
        composeRule.setContent {
            SteeringTemplateSheet(onTemplateTap = {})
        }
        composeRule.onNodeWithTag("steering_template_continue").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_template_abort").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_template_explain").assertIsDisplayed()
    }

    @Test
    fun hiddenState_rendersNothing() {
        composeRule.setContent {
            SteeringBar(
                state = SteeringBarState.Hidden,
                controller = SteeringBarController(CoroutineScope(Dispatchers.Main))
            )
        }
        composeRule.onNodeWithTag("steering_bar").assertDoesNotExist()
    }
}
