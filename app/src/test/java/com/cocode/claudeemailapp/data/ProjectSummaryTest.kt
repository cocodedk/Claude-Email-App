package com.cocode.claudeemailapp.data

import com.cocode.claudeemailapp.protocol.AgentStatusValues
import com.cocode.claudeemailapp.protocol.EnvelopeJson
import com.cocode.claudeemailapp.protocol.TaskStateValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSummaryTest {

    @Test
    fun `parses ack data with full project list`() {
        val raw = """
            {
              "projects": [
                {
                  "name": "claude-email",
                  "path": "/home/cocodedk/0-projects/claude-email",
                  "running_task_id": 42,
                  "queue_depth": 2,
                  "last_activity_at": "2026-05-03T09:24:00Z"
                },
                {
                  "name": "babakcast",
                  "path": "/home/cocodedk/0-projects/babakcast",
                  "running_task_id": null,
                  "queue_depth": 0,
                  "last_activity_at": null
                }
              ]
            }
        """.trimIndent()

        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(2, response.projects.size)
        val first = response.projects[0]
        assertEquals("claude-email", first.name)
        assertEquals("/home/cocodedk/0-projects/claude-email", first.path)
        assertEquals(42L, first.runningTaskId)
        assertEquals(2, first.queueDepth)
        assertEquals("2026-05-03T09:24:00Z", first.lastActivityAt)

        val second = response.projects[1]
        assertNull(second.runningTaskId)
        assertEquals(0, second.queueDepth)
        assertNull(second.lastActivityAt)
    }

    @Test
    fun `parses empty project list`() {
        val response = EnvelopeJson.decodeFromString(
            ListProjectsResponse.serializer(),
            """{"projects":[]}"""
        )
        assertTrue(response.projects.isEmpty())
    }

    @Test
    fun `parses agent_status wire strings`() {
        val raw = """
            {
              "projects": [
                {"name": "a", "path": "/a", "agent_status": "connected"},
                {"name": "b", "path": "/b", "agent_status": "disconnected"},
                {"name": "c", "path": "/c", "agent_status": "absent"},
                {"name": "d", "path": "/d"}
              ]
            }
        """.trimIndent()
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(AgentStatusValues.CONNECTED, response.projects[0].agentStatus)
        assertEquals(AgentStatusValues.DISCONNECTED, response.projects[1].agentStatus)
        assertEquals(AgentStatusValues.ABSENT, response.projects[2].agentStatus)
        assertNull(response.projects[3].agentStatus)
    }

    @Test
    fun `unknown agent_status string passes through verbatim`() {
        val raw = """{"projects":[{"name":"x","path":"/x","agent_status":"future_state"}]}"""
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals("future_state", response.projects[0].agentStatus)
    }

    @Test
    fun `parses v2 agent_status vocab`() {
        val raw = """
            {
              "projects": [
                {"name": "a", "path": "/a", "agent_status": "online"},
                {"name": "b", "path": "/b", "agent_status": "stale"},
                {"name": "c", "path": "/c", "agent_status": "offline"}
              ]
            }
        """.trimIndent()
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(AgentStatusValues.ONLINE, response.projects[0].agentStatus)
        assertEquals(AgentStatusValues.STALE, response.projects[1].agentStatus)
        assertEquals(AgentStatusValues.OFFLINE, response.projects[2].agentStatus)
    }

    @Test
    fun `parses v2 task_state vocab and null`() {
        val raw = """
            {
              "projects": [
                {"name": "a", "path": "/a", "task_state": "waiting"},
                {"name": "b", "path": "/b", "task_state": "working"},
                {"name": "c", "path": "/c", "task_state": "completed"},
                {"name": "d", "path": "/d", "task_state": "error"},
                {"name": "e", "path": "/e", "task_state": null},
                {"name": "f", "path": "/f"}
              ]
            }
        """.trimIndent()
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(TaskStateValues.WAITING, response.projects[0].taskState)
        assertEquals(TaskStateValues.WORKING, response.projects[1].taskState)
        assertEquals(TaskStateValues.COMPLETED, response.projects[2].taskState)
        assertEquals(TaskStateValues.ERROR, response.projects[3].taskState)
        assertNull(response.projects[4].taskState)
        assertNull(response.projects[5].taskState)
    }

    @Test
    fun `v1 response without task_state still parses cleanly`() {
        val raw = """
            {
              "projects": [
                {
                  "name": "legacy",
                  "path": "/legacy",
                  "running_task_id": 7,
                  "agent_status": "connected"
                }
              ]
            }
        """.trimIndent()
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(AgentStatusValues.CONNECTED, response.projects[0].agentStatus)
        assertNull(response.projects[0].taskState)
    }

    @Test
    fun `unknown task_state passes through verbatim`() {
        val raw = """{"projects":[{"name":"x","path":"/x","task_state":"future_state"}]}"""
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals("future_state", response.projects[0].taskState)
    }

    @Test
    fun `isLive treats both vocabs as reachable`() {
        assertTrue(AgentStatusValues.isLive(AgentStatusValues.ONLINE))
        assertTrue(AgentStatusValues.isLive(AgentStatusValues.CONNECTED))
        assertFalse(AgentStatusValues.isLive(AgentStatusValues.STALE))
        assertFalse(AgentStatusValues.isLive(AgentStatusValues.OFFLINE))
        assertFalse(AgentStatusValues.isLive(AgentStatusValues.DISCONNECTED))
        assertFalse(AgentStatusValues.isLive(AgentStatusValues.ABSENT))
        assertFalse(AgentStatusValues.isLive(null))
    }

    @Test
    fun `tolerates unknown fields per existing EnvelopeJson contract`() {
        val raw = """
            {
              "projects": [
                {
                  "name": "x",
                  "path": "/x",
                  "queue_depth": 0,
                  "future_field": "ignored"
                }
              ],
              "future_top_level": true
            }
        """.trimIndent()
        val response = EnvelopeJson.decodeFromString(ListProjectsResponse.serializer(), raw)
        assertEquals(1, response.projects.size)
        assertEquals("x", response.projects[0].name)
    }
}
