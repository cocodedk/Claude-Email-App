package com.cocode.claudeemailapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activity_createsViewModelViaFactory_andRendersRoot() {
        composeRule.waitForIdle()
        val setupCount = composeRule.onAllNodesWithTag("setup_screen").fetchSemanticsNodes().size
        val homeCount = composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().size
        assert(setupCount + homeCount > 0) {
            "App did not render setup or home screen after Factory-created ViewModel"
        }
    }
}
