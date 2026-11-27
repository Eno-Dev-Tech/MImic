package com.example.mimic.data

import com.example.mimic.ui.designsystem.ThemeRegistry

/** Stable IDs only. Unknown removed IDs are retained, so later imports do not undo a choice. */
object ThemeVisibility {
    data class Removal(val removedIds: Set<String>, val selectedId: String)

    fun visibleIds(knownIds: List<String>, removedIds: Set<String>): List<String> {
        val known = knownIds.distinct()
        return known.filterNot { it in removedIds }.ifEmpty {
            // Defensive fallback for a damaged/imported preference set; never show an empty gallery.
            known.firstOrNull { it == ThemeRegistry.DEFAULT_ID }?.let(::listOf)
                ?: known.take(1)
        }
    }

    fun fallbackId(visibleIds: List<String>): String =
        visibleIds.firstOrNull { it == ThemeRegistry.DEFAULT_ID } ?: visibleIds.first()

    fun remove(knownIds: List<String>, removedIds: Set<String>, selectedId: String, id: String): Removal? {
        val visible = visibleIds(knownIds, removedIds)
        if (id !in visible || visible.size <= 1) return null
        val removed = removedIds + id
        val remaining = visibleIds(knownIds, removed)
        return Removal(removed, selectedId.takeIf { it in remaining } ?: fallbackId(remaining))
    }
}
