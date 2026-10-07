package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.BuildConfig

/** The places the About screen can send the reader to. */
enum class AboutLink { Update, Privacy, Website, Source, Issues }

private const val REPO = "https://github.com/cocodedk/Claude-Email-App"
private const val SITE = "https://cocodedk.github.io/Claude-Email-App"

/**
 * True once apps.yml in cocode-apps says the app is live on F-Droid. Until then
 * "See the latest version" opens the newest GitHub release. Flip it when the app is accepted.
 */
const val LIVE_ON_FDROID = false

/**
 * The privacy page that the privacy-policy pull request publishes on the app's website (the
 * `privacy` value in apps.yml in cocode-apps). Set it to null to leave the "Read the privacy
 * policy" button out of the About screen.
 */
val PRIVACY_URL: String? = "https://cocodedk.github.io/Claude-Email-App/privacy/"

/**
 * Where each About link goes. The update link is the F-Droid page when [liveOnFdroid] is true and
 * the latest GitHub release otherwise; the app never checks for updates over the network. The
 * privacy link is null when there is no [privacyUrl].
 */
fun aboutUrl(
    link: AboutLink,
    liveOnFdroid: Boolean = LIVE_ON_FDROID,
    privacyUrl: String? = PRIVACY_URL
): String? = when (link) {
    AboutLink.Update ->
        if (liveOnFdroid) "https://f-droid.org/packages/${BuildConfig.APPLICATION_ID}/" else "$REPO/releases/latest"
    AboutLink.Privacy -> privacyUrl
    AboutLink.Website -> SITE
    AboutLink.Source -> REPO
    AboutLink.Issues -> "$REPO/issues"
}
