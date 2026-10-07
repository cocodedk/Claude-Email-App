package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.AppViewModel
import com.cocode.claudeemailapp.app.HomeScreen
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Home header on a 360 dp screen: counters and tabs must never break a word in two, and at
 * normal text the tab row must still fill the screen.
 */
@RunWith(AndroidJUnit4::class)
class HomeHeaderLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ClaudeEmailAppTheme {
                    Box(Modifier.width(360.dp)) {
                        HomeScreen(
                            state = AppViewModel.InboxState(),
                            buckets = AppViewModel.HomeBuckets(),
                            pending = emptyList(),
                            onRefresh = {},
                            onOpenConversation = {},
                            onCompose = {},
                            onOpenSettings = {},
                            onArchiveToggle = {}
                        )
                    }
                }
            }
        }
    }

    private fun lineCount(text: String): Int {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().lineCount
    }

    private fun assertOneLine(texts: List<String>, fontScale: Float) {
        render(fontScale)
        texts.forEach { assertEquals("'$it' at font scale $fontScale", 1, lineCount(it)) }
    }

    private val counters = listOf("ACTIVE", "WAITING", "ARCHIVED")
    private val tabs = listOf("Active", "Waiting", "Archived")

    @Test fun counters_stayWhole_atNormalText() = assertOneLine(counters, 1f)
    @Test fun counters_stayWhole_at1_3xText() = assertOneLine(counters, 1.3f)
    @Test fun counters_stayWhole_at2xText() = assertOneLine(counters, 2f)

    @Test fun tabs_stayWhole_atNormalText() = assertOneLine(tabs, 1f)
    @Test fun tabs_stayWhole_at1_3xText() = assertOneLine(tabs, 1.3f)
    @Test fun tabs_stayWhole_at2xText() = assertOneLine(tabs, 2f)

    @Test
    fun tabs_fillTheScreen_atNormalText() {
        render(1f)
        val width = composeRule.onNodeWithTag("home_filter_tabs").getUnclippedBoundsInRoot().let { it.right - it.left }
        // 360 dp minus the 20 dp list padding on each side.
        assertTrue("tab row is $width wide", width >= 319.dp)
    }
}
