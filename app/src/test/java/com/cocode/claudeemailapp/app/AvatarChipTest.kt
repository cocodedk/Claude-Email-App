package com.cocode.claudeemailapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarChipTest {

    @Test
    fun displayName_twoWords_takesFirstLetterOfEach() {
        assertEquals("AB", initialsFor("Alice Bob", "x@y"))
    }

    @Test
    fun displayName_oneWord_takesFirstTwoLetters() {
        assertEquals("AL", initialsFor("Alice", "x@y"))
    }

    @Test
    fun displayName_blank_fallsBackToEmailLocalPart() {
        assertEquals("AG", initialsFor("", "agent@example.com"))
    }

    @Test
    fun underscoreSeparated_local_treatedAsTwoWords() {
        assertEquals("WW", initialsFor("", "wake_watcher@example.com"))
    }

    @Test
    fun emptyEverything_returnsQuestionMark() {
        assertEquals("?", initialsFor("", ""))
    }

    @Test
    fun dotSeparated_local_treatedAsTwoWords() {
        assertEquals("BB", initialsFor("babak.bandpey", "x@y"))
    }
}
