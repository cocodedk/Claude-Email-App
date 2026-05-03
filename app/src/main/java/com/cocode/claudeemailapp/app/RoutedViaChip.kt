package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.protocol.RoutedVia

/**
 * Short chip label for the backend's `meta.routed_via` ack stamp — tells the
 * user whether their command went to the live chat-bus agent or spawned a
 * fresh worker. Returns null for missing/unknown values so legacy acks (no
 * stamp) and future enum extensions don't render a misleading chip.
 */
fun routedViaChipLabel(routedVia: String?): String? = when (routedVia) {
    RoutedVia.AGENT -> "via agent"
    RoutedVia.AGENT_QUEUED -> "via agent · queued"
    RoutedVia.WORKER -> "via worker"
    else -> null
}
