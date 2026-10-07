package com.cocode.claudeemailapp.app

import androidx.annotation.StringRes
import com.cocode.claudeemailapp.R
import com.cocode.claudeemailapp.protocol.RoutedVia

/**
 * Short chip label (a string resource) for the backend's `meta.routed_via` ack stamp — tells the
 * user whether their command went to the live chat-bus agent or spawned a
 * fresh worker. Returns null for missing/unknown values so legacy acks (no
 * stamp) and future enum extensions don't render a misleading chip.
 */
@StringRes
fun routedViaChipLabel(routedVia: String?): Int? = when (routedVia) {
    RoutedVia.AGENT -> R.string.routed_via_agent
    RoutedVia.AGENT_QUEUED -> R.string.routed_via_agent_queued
    RoutedVia.WORKER -> R.string.routed_via_worker
    else -> null
}
