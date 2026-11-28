/*
 * Adapted from Musify lib/services/common_services.dart (_fetchSkipSegments).
 * Copyright (C) 2026 Valeri Gokadze; GPL-3.0-or-later.
 * Modified for MImic: millisecond precision, validation, overlapping ranges,
 * bounded cache and a single Media3 player rather than clipped audio sources.
 */
package com.example.mimic.player

import org.json.JSONArray

data class SponsorSegment(val startMs: Long, val endMs: Long)

object SponsorSegments {
    val categories = setOf("sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic")
    fun parse(json: String): List<SponsorSegment> {
        val array = JSONArray(json)
        require(array.length() <= 1000) { "Too many segments" }
        val segments = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("actionType", "skip") != "skip" || item.optString("category") !in categories) return@mapNotNull null
            val range = item.optJSONArray("segment") ?: return@mapNotNull null
            if (range.length() != 2) return@mapNotNull null
            val start = range.optDouble(0)
            val end = range.optDouble(1)
            if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start || end > 604800) return@mapNotNull null
            SponsorSegment((start * 1000).toLong(), (end * 1000).toLong())
        }.sortedBy { it.startMs }
        val merged = mutableListOf<SponsorSegment>()
        for (s in segments) {
            val last = merged.lastOrNull()
            if (last != null && s.startMs <= last.endMs) merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, s.endMs))
            else merged.add(s)
        }
        return merged
    }

    fun skipTarget(segments: List<SponsorSegment>, positionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0) return null
        val segment = segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs } ?: return null
        // Don't jump to an unbounded/unknown timeline or past the end of the recording.
        return segment.endMs.coerceAtMost(durationMs).takeIf { it > positionMs }
    }
}
