package com.cocode.claudeemailapp.app

import com.cocode.claudeemailapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutedViaChipTest {

    @Test fun `agent maps to via agent`() = assertEquals(R.string.routed_via_agent, routedViaChipLabel("agent"))
    @Test fun `agent_queued maps to via agent queued`() =
        assertEquals(R.string.routed_via_agent_queued, routedViaChipLabel("agent_queued"))
    @Test fun `worker maps to via worker`() = assertEquals(R.string.routed_via_worker, routedViaChipLabel("worker"))
    @Test fun `null returns null`() = assertNull(routedViaChipLabel(null))
    @Test fun `unknown wire value returns null`() = assertNull(routedViaChipLabel("future_value"))
}
