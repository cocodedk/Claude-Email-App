package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.steering.SteeringBar
import com.cocode.claudeemailapp.app.steering.SteeringBarController
import com.cocode.claudeemailapp.app.steering.SteeringBarState
import com.cocode.claudeemailapp.app.steering.SteeringTemplateSheet
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manual demo: walks through all five steering-bar scenes, holding each
 * on screen for 4 seconds. Not an assertion test — there to *see* the bar
 * on a real device / emulator without running the full app. Invoke via:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.cocode.claudeemailapp.SteeringDemoTest
 */
@RunWith(AndroidJUnit4::class)
class SteeringDemoTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scenes = listOf(
        "1 / 5   Idle — Status · Cancel · More, all enabled",
        "2 / 5   Armed — Cancel flips to red \"Cancel · confirm\"",
        "3 / 5   Sending — Status chip becomes \"Sending…\", peers disable",
        "4 / 5   Awaiting user — three reply templates",
        "5 / 5   Hidden — bar disappears (task finished)"
    )

    @Test
    fun walkThroughSteeringStates() {
        // Freeze the test clock so the armed state doesn't auto-disarm
        // mid-demo when its 3s timeout would fire.
        composeRule.mainClock.autoAdvance = false

        var sceneIdx by mutableIntStateOf(0)

        composeRule.setContent {
            ClaudeEmailAppTheme {
                val scope = rememberCoroutineScope()
                val controller = remember(sceneIdx) {
                    SteeringBarController(scope).also {
                        when (sceneIdx) {
                            1 -> it.tapCancel()   // first tap arms the chip
                            2 -> it.tapStatus()   // fire a Status intent → sending
                        }
                    }
                }
                val state = when (sceneIdx) {
                    3 -> SteeringBarState.AwaitingUser("42")
                    4 -> SteeringBarState.Hidden
                    else -> SteeringBarState.Idle
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF050506))
                        .padding(top = 80.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = scenes[sceneIdx],
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    when (state) {
                        SteeringBarState.Idle -> SteeringBar(state = state, controller = controller)
                        is SteeringBarState.AwaitingUser -> SteeringTemplateSheet(onTemplateTap = {})
                        SteeringBarState.Hidden -> Text(
                            text = "(steering bar hidden)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF6E757F)
                        )
                    }
                }
            }
        }

        // Pump each scene: flip the index, nudge the clock to force a
        // recomposition, wait for layout to settle, then hold it on screen.
        repeat(scenes.size) { i ->
            sceneIdx = i
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            Thread.sleep(4_000)
        }
    }
}
