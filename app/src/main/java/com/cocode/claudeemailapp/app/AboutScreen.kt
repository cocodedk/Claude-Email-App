package com.cocode.claudeemailapp.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cocode.claudeemailapp.R

/**
 * The About screen, in the cocode-apps order: name and version, what the app does, privacy,
 * links, credits and licenses, made by Cocode. Every title is a TalkBack heading.
 *
 * [onOpenLink] returns false when no app on the phone can open the link; the screen then says so
 * under the section that holds the button. [showPrivacyLink] is false while there is no privacy
 * page to point to.
 */
@Composable
fun AboutScreen(
    version: String,
    showPrivacyLink: Boolean,
    onOpenLink: (AboutLink) -> Boolean,
    onBack: () -> Unit
) {
    var failedLink by rememberSaveable { mutableStateOf<AboutLink?>(null) }
    val open = { link: AboutLink -> failedLink = if (onOpenLink(link)) null else link }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("about_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("about_back")) {
                Text(stringResource(R.string.action_back))
            }
        }
        AboutSection(stringResource(R.string.app_name), MaterialTheme.typography.headlineSmall) {
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { open(AboutLink.Update) },
                modifier = Modifier.fillMaxWidth().testTag("about_update")
            ) { Text(stringResource(R.string.about_check_updates)) }
            AboutNote(R.string.about_update_note)
            if (failedLink == AboutLink.Update) AboutNote(R.string.about_no_browser)
        }
        AboutSection(stringResource(R.string.about_what_title)) { AboutBody(R.string.about_what_body) }
        AboutSection(stringResource(R.string.about_privacy_title)) {
            AboutBody(R.string.about_privacy_nothing_to_cocode)
            AboutBody(R.string.about_privacy_servers)
            AboutBody(R.string.about_privacy_stored)
            AboutBody(R.string.about_privacy_commands)
            if (showPrivacyLink) {
                LinkButton(R.string.about_privacy_link, "about_privacy") { open(AboutLink.Privacy) }
                if (failedLink == AboutLink.Privacy) AboutNote(R.string.about_no_browser)
            }
        }
        AboutSection(stringResource(R.string.about_links_title)) {
            LinkButton(R.string.about_website, "about_website") { open(AboutLink.Website) }
            LinkButton(R.string.about_source, "about_source") { open(AboutLink.Source) }
            LinkButton(R.string.about_report, "about_report") { open(AboutLink.Issues) }
            if (failedLink in listOf(AboutLink.Website, AboutLink.Source, AboutLink.Issues)) {
                AboutNote(R.string.about_no_browser)
            }
        }
        AboutSection(stringResource(R.string.about_credits)) {
            AboutBody(R.string.about_credit_license)
            AboutBody(R.string.about_credit_angus)
            AboutBody(R.string.about_credit_androidx)
        }
        AboutSection(stringResource(R.string.about_made_by)) {}
        // Support slot (see cocode-apps standard/support.md): nothing is shown here until the
        // Support phase adds its section after "Made by Cocode".
    }
}

@Composable
private fun AboutSection(
    title: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = titleStyle, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun AboutBody(@StringRes textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AboutNote(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LinkButton(@StringRes labelRes: Int, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(stringResource(labelRes))
    }
}

/** Hands a link to the phone's browser. False when no app can open it. */
@Composable
fun rememberLinkOpener(): (AboutLink) -> Boolean {
    val context = LocalContext.current
    return remember(context) { { link -> aboutUrl(link)?.let { openWebPage(context, it) } ?: false } }
}

private fun openWebPage(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}
