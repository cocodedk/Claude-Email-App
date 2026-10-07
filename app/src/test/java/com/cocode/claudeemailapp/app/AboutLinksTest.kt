package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AboutLinksTest {

    @Test
    fun update_notOnFdroid_opensTheLatestGithubRelease() {
        assertEquals(
            "https://github.com/cocodedk/Claude-Email-App/releases/latest",
            aboutUrl(AboutLink.Update, liveOnFdroid = false)
        )
    }

    @Test
    fun update_liveOnFdroid_opensTheFdroidPage() {
        assertEquals(
            "https://f-droid.org/packages/com.cocode.claudeemailapp/",
            aboutUrl(AboutLink.Update, liveOnFdroid = true)
        )
    }

    @Test
    fun defaults_followTheConstantsInTheCode() {
        // Once F-Droid accepts the app, only the constant changes.
        assertEquals(aboutUrl(AboutLink.Update, liveOnFdroid = LIVE_ON_FDROID), aboutUrl(AboutLink.Update))
        assertEquals(PRIVACY_URL, aboutUrl(AboutLink.Privacy))
    }

    @Test
    fun privacy_defaultIsThePolicyPageOnTheWebsite() {
        assertEquals(
            "https://cocodedk.github.io/Claude-Email-App/privacy/",
            aboutUrl(AboutLink.Privacy)
        )
    }

    @Test
    fun privacy_withAPolicyPage_opensIt() {
        assertEquals(
            "https://example.org/privacy/",
            aboutUrl(AboutLink.Privacy, privacyUrl = "https://example.org/privacy/")
        )
    }

    @Test
    fun privacy_withoutAPolicyPage_hasNoLink() {
        assertNull(aboutUrl(AboutLink.Privacy, privacyUrl = null))
    }

    @Test
    fun websiteSourceAndIssues_doNotDependOnTheFlags() {
        for (onFdroid in listOf(false, true)) {
            for (privacy in listOf(null, "https://example.org/privacy/")) {
                assertEquals(
                    "https://cocodedk.github.io/Claude-Email-App",
                    aboutUrl(AboutLink.Website, onFdroid, privacy)
                )
                assertEquals(
                    "https://github.com/cocodedk/Claude-Email-App",
                    aboutUrl(AboutLink.Source, onFdroid, privacy)
                )
                assertEquals(
                    "https://github.com/cocodedk/Claude-Email-App/issues",
                    aboutUrl(AboutLink.Issues, onFdroid, privacy)
                )
            }
        }
    }
}
