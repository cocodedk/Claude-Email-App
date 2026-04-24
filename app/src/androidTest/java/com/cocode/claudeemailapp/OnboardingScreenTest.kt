package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.OnboardingScreen
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstPage_showsNextButtonAndFirstTitle() {
        composeRule.setContent { ClaudeEmailAppTheme { OnboardingScreen(onFinish = {}) } }
        composeRule.onNodeWithText("Email Claude. It runs the task.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").assertIsDisplayed()
    }

    @Test
    fun next_advancesThroughPages_untilGetStarted() {
        composeRule.setContent { ClaudeEmailAppTheme { OnboardingScreen(onFinish = {}) } }
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText("Threads, not an inbox.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithText("Credentials stay on device.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_start").assertIsDisplayed()
    }

    @Test
    fun getStarted_invokesOnFinish() {
        var finished = false
        composeRule.setContent { ClaudeEmailAppTheme { OnboardingScreen(onFinish = { finished = true }) } }
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithTag("onboarding_next").performClick()
        composeRule.onNodeWithTag("onboarding_start").performClick()
        assert(finished)
    }

    @Test
    fun skip_invokesOnFinish_fromFirstPage() {
        var finished = false
        composeRule.setContent { ClaudeEmailAppTheme { OnboardingScreen(onFinish = { finished = true }) } }
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        assert(finished)
    }
}
