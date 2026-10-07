package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AboutLinksTest {

    @Test
    fun update_notOnFdroid_opensTheLatestGithubRelease() {
        assertEquals(
            "https://github.com/cocodedk/Claude-Email-App/releases/latest",
            aboutUrl(AboutLink.Update, "en", liveOnFdroid = false)
        )
    }

    @Test
    fun update_liveOnFdroid_opensTheFdroidPage() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.claudeemailapp/",
            aboutUrl(AboutLink.Update, "en", liveOnFdroid = true)
        )
    }

    @Test
    fun defaults_followTheConstantsInTheCode() {
        // Once F-Droid accepts the app, only the constant changes.
        assertEquals(aboutUrl(AboutLink.Update, "en", liveOnFdroid = LIVE_ON_FDROID), aboutUrl(AboutLink.Update, "en"))
        assertEquals(PRIVACY_URL, aboutUrl(AboutLink.Privacy, "en"))
    }

    @Test
    fun privacy_defaultIsThePolicyPageOnTheWebsite() {
        assertEquals(
            "https://cocodedk.github.io/Claude-Email-App/privacy/",
            aboutUrl(AboutLink.Privacy, "en")
        )
    }

    @Test
    fun privacy_withAPolicyPage_opensIt() {
        assertEquals(
            "https://example.org/privacy/",
            aboutUrl(AboutLink.Privacy, "da", privacyUrl = "https://example.org/privacy/")
        )
    }

    @Test
    fun privacy_withoutAPolicyPage_hasNoLink() {
        assertNull(aboutUrl(AboutLink.Privacy, "da", privacyUrl = null))
    }

    @Test
    fun english_opensTheEnglishPages() {
        assertEquals("https://cocodedk.github.io/Claude-Email-App", aboutUrl(AboutLink.Website, "en"))
        assertEquals("https://cocodedk.github.io/Claude-Email-App/privacy/", aboutUrl(AboutLink.Privacy, "en"))
    }

    @Test
    fun danish_opensTheDanishPages() {
        assertEquals("https://cocodedk.github.io/Claude-Email-App/da/", aboutUrl(AboutLink.Website, "da"))
        assertEquals("https://cocodedk.github.io/Claude-Email-App/da/privacy/", aboutUrl(AboutLink.Privacy, "da"))
    }

    @Test
    fun aLanguageTheSiteLacks_opensTheEnglishPages() {
        // The site has a Persian home page but no Persian privacy page, so Persian stays on English;
        // German has no pages at all.
        for (language in listOf("fa", "de", "")) {
            assertEquals("https://cocodedk.github.io/Claude-Email-App", aboutUrl(AboutLink.Website, language))
            assertEquals(
                "https://cocodedk.github.io/Claude-Email-App/privacy/",
                aboutUrl(AboutLink.Privacy, language)
            )
        }
    }

    @Test
    fun sourceAndIssues_doNotDependOnTheOtherSettings() {
        for (language in listOf("en", "da", "fa")) {
            for (onFdroid in listOf(false, true)) {
                for (privacy in listOf(null, "https://example.org/privacy/")) {
                    assertEquals(
                        aboutUrl(AboutLink.Website, language),
                        aboutUrl(AboutLink.Website, language, onFdroid, privacy)
                    )
                    assertEquals(
                        "https://github.com/cocodedk/Claude-Email-App",
                        aboutUrl(AboutLink.Source, language, onFdroid, privacy)
                    )
                    assertEquals(
                        "https://github.com/cocodedk/Claude-Email-App/issues",
                        aboutUrl(AboutLink.Issues, language, onFdroid, privacy)
                    )
                }
            }
        }
    }
}
