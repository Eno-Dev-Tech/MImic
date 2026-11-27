package com.example.mimic

import com.example.mimic.data.ThemeVisibility
import com.example.mimic.ui.designsystem.ThemeRegistry
import org.junit.Assert.*
import org.junit.Test

class ThemeVisibilityTest {
    private val ids = listOf(ThemeRegistry.DEFAULT_ID, "felt_classic", "felt_orange", "custom-123")

    @Test fun activeRemovalChoosesVisibleDefaultAndPreservesOtherChoices() {
        val result = ThemeVisibility.remove(ids, setOf("missing-import"), "felt_orange", "felt_orange")!!
        assertEquals(ThemeRegistry.DEFAULT_ID, result.selectedId)
        assertEquals(setOf("missing-import", "felt_orange"), result.removedIds)
        assertEquals("custom-123", ThemeVisibility.remove(ids, emptySet(), "custom-123", "felt_classic")!!.selectedId)
    }

    @Test fun hiddenDefaultCannotBeFallbackAndLastThemeCannotBeRemoved() {
        val hidden = setOf(ThemeRegistry.DEFAULT_ID, "felt_classic")
        val result = ThemeVisibility.remove(ids, hidden, "felt_orange", "felt_orange")!!
        assertEquals("custom-123", result.selectedId)
        assertNull(ThemeVisibility.remove(ids, result.removedIds, result.selectedId, "custom-123"))
        assertNull(ThemeVisibility.remove(ids, hidden, "felt_orange", "unknown"))
    }

    @Test fun idsSurviveReorderingAndNewThemesAppearAutomatically() {
        assertEquals(listOf("custom-123", "felt_classic", ThemeRegistry.DEFAULT_ID, "new-theme"),
            ThemeVisibility.visibleIds(ids.reversed() + "new-theme", setOf("felt_orange")))
        assertEquals(listOf(ThemeRegistry.DEFAULT_ID), ThemeVisibility.visibleIds(ids, ids.toSet()))
        assertEquals(ids, ThemeVisibility.visibleIds(ids, emptySet()))
    }
}
