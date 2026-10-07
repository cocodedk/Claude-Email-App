package com.cocode.claudeemailapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.claudeemailapp.app.AboutLink
import com.cocode.claudeemailapp.app.AboutScreen
import com.cocode.claudeemailapp.ui.theme.ClaudeEmailAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<AboutLink>()

    private fun render(showPrivacyLink: Boolean = false, canOpen: Boolean = true) {
        composeRule.setContent {
            ClaudeEmailAppTheme {
                AboutScreen(
                    version = "1.2.3",
                    showPrivacyLink = showPrivacyLink,
                    onOpenLink = { opened += it; canOpen },
                    onBack = {}
                )
            }
        }
    }

    private fun isHeading(title: String) =
        hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun showsNameVersionAndUpdateButton() {
        render()
        composeRule.onNodeWithText("Claude Email App").assertIsDisplayed()
        composeRule.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        composeRule.onNodeWithText("See the latest version").assertIsDisplayed()
    }

    @Test
    fun everySectionTitleIsAHeading() {
        render()
        listOf(
            "Claude Email App",
            "What the app does",
            "Privacy",
            "Links",
            "Credits and licenses",
            "Made by Cocode (cocode.dk)"
        ).forEach { title ->
            composeRule.onNodeWithText(title).performScrollTo()
            composeRule.onAllNodes(isHeading(title)).assertCountEquals(1)
        }
    }

    @Test
    fun updateButton_asksForTheUpdateLink() {
        render()
        composeRule.onNodeWithTag("about_update").performClick()
        assertEquals(listOf(AboutLink.Update), opened)
    }

    @Test
    fun linkButtons_askForTheirLinks() {
        render()
        composeRule.onNodeWithTag("about_website").performScrollTo().performClick()
        composeRule.onNodeWithTag("about_source").performScrollTo().performClick()
        composeRule.onNodeWithTag("about_report").performScrollTo().performClick()
        assertEquals(listOf(AboutLink.Website, AboutLink.Source, AboutLink.Issues), opened)
    }

    @Test
    fun withoutAPrivacyPage_thePrivacyButtonIsLeftOut() {
        render(showPrivacyLink = false)
        composeRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodes(hasText("Read the privacy policy")).assertCountEquals(0)
    }

    @Test
    fun withAPrivacyPage_thePrivacyButtonOpensIt() {
        render(showPrivacyLink = true)
        composeRule.onNodeWithTag("about_privacy").performScrollTo().performClick()
        assertEquals(listOf(AboutLink.Privacy), opened)
    }

    @Test
    fun whenNoAppCanOpenALink_theScreenSaysSo() {
        render(canOpen = false)
        composeRule.onNodeWithTag("about_update").performClick()
        composeRule.onNodeWithText(
            "No app on this phone can open the link. Install a web browser, then try again."
        ).assertIsDisplayed()
    }
}
