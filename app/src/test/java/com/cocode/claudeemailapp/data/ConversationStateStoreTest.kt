package com.cocode.claudeemailapp.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationStateStoreTest {

    private lateinit var store: SharedPrefsConversationStateStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("css_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = SharedPrefsConversationStateStore(prefs)
    }

    @Test
    fun loadArchivedIds_emptyByDefault() {
        assertTrue(store.loadArchivedIds().isEmpty())
    }

    @Test
    fun saveAndLoad_roundTrip() {
        store.saveArchivedIds(setOf("<a>", "<b>"))
        assertEquals(setOf("<a>", "<b>"), store.loadArchivedIds())
    }

    @Test
    fun saveEmpty_clearsArchive() {
        store.saveArchivedIds(setOf("<a>"))
        store.saveArchivedIds(emptySet())
        assertTrue(store.loadArchivedIds().isEmpty())
    }

    @Test
    fun saveTwice_overwritesPrevious() {
        store.saveArchivedIds(setOf("<a>", "<b>"))
        store.saveArchivedIds(setOf("<c>"))
        assertEquals(setOf("<c>"), store.loadArchivedIds())
    }

    @Test
    fun loadHasSeenOnboarding_falseByDefault() {
        org.junit.Assert.assertFalse(store.loadHasSeenOnboarding())
    }

    @Test
    fun markOnboardingSeen_persistsTrue() {
        store.markOnboardingSeen()
        org.junit.Assert.assertTrue(store.loadHasSeenOnboarding())
    }

    @Test
    fun markOnboardingSeen_isIdempotent() {
        store.markOnboardingSeen()
        store.markOnboardingSeen()
        org.junit.Assert.assertTrue(store.loadHasSeenOnboarding())
    }

    @Test
    fun loadRecentProjects_emptyByDefault() {
        assertTrue(store.loadRecentProjects().isEmpty())
    }

    @Test
    fun pushRecentProject_mostRecentFirst() {
        store.pushRecentProject("alpha")
        store.pushRecentProject("beta")
        assertEquals(listOf("beta", "alpha"), store.loadRecentProjects())
    }

    @Test
    fun pushRecentProject_deduplicatesByValue() {
        store.pushRecentProject("alpha")
        store.pushRecentProject("beta")
        store.pushRecentProject("alpha")
        assertEquals(listOf("alpha", "beta"), store.loadRecentProjects())
    }

    @Test
    fun pushRecentProject_trimsAndIgnoresBlank() {
        store.pushRecentProject("  alpha  ")
        store.pushRecentProject("")
        store.pushRecentProject("   ")
        assertEquals(listOf("alpha"), store.loadRecentProjects())
    }

    @Test
    fun pushRecentProject_capsAtFive() {
        repeat(7) { store.pushRecentProject("p$it") }
        assertEquals(
            listOf("p6", "p5", "p4", "p3", "p2"),
            store.loadRecentProjects()
        )
    }
}
