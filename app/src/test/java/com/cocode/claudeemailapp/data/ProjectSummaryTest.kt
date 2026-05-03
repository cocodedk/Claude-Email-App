package com.cocode.claudeemailapp.data

import com.cocode.claudeemailapp.protocol.EnvelopeJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
