package com.cocode.claudeemailapp.e2e

import android.Manifest
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cocode.claudeemailapp.data.CredentialsStore
import org.junit.rules.ExternalResource

/**
 * Everything that must be true *before* the activity starts.
 *
 * Ordering is the whole reason this is a rule rather than a `@Before`: JUnit
 * runs `@Before` inside the activity rule, i.e. after launch, and by then it is
 * already too late for both of these.
 *
 * * The trust anchor. Angus Mail resolves `SSLSocketFactory.getDefault()`,
 *   which the platform caches process-wide on first use. If the app opened an
 *   IMAP connection before [E2eTrust.installAsDefault] ran, the system trust
 *   store would be pinned for the rest of the process and every later
 *   connection would fail.
 * * The credential store. A previous run can leave the app signed in, in which
 *   case it starts polling IMAP the moment it launches — which is exactly the
 *   early connection above. Clearing first also puts the app on the Setup
 *   screen, so the test drives the real Setup → probe → save path.
 *
 * The negative control runs first of all, while the platform trust store is
 * still the effective one, and it deliberately builds its own SSLContext rather
 * than touching the cached default. See [E2eTrust.assertUntrustedBySystem].
 *
 * The notification permission is granted here for the same ordering reason:
 * `MainActivity.onCreate` requests it, and on API 33+ the resulting system
 * dialog opens over the app and pauses it, so the composition never becomes
 * reachable and every UI query times out. Granting up front means the app
 * never asks. This changes nothing the test asserts — it removes a system
 * dialog, not a code path in the app.
 */
class E2eEnvironmentRule(private val config: E2eConfig) : ExternalResource() {

    override fun before() {
        grantNotificationPermission()
        E2eTrust.assertUntrustedBySystem(E2eConfig.HOST, config.imapsPort)
        E2eTrust.installAsDefault(config.caPem)
        CredentialsStore(ApplicationProvider.getApplicationContext()).clear()
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            ).close()
    }
}
