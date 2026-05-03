package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutedViaChipTest {

    @Test fun `agent maps to via agent`() = assertEquals("via agent", routedViaChipLabel("agent"))
    @Test fun `agent_queued maps to via agent queued`() =
        assertEquals("via agent · queued", routedViaChipLabel("agent_queued"))
    @Test fun `worker maps to via worker`() = assertEquals("via worker", routedViaChipLabel("worker"))
    @Test fun `null returns null`() = assertNull(routedViaChipLabel(null))
    @Test fun `unknown wire value returns null`() = assertNull(routedViaChipLabel("future_value"))
}
