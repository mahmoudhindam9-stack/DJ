package com.example.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranMushafGeometryTest {

    @Test
    fun `single line active verse underline trims before marker medallion`() {
        // Line 1 center Y = 24.70f; verse spans x in [60, 290] with marker at (70, 24.7)
        val polygon = listOf(
            MushafPolygonPoint(60f, 10f),
            MushafPolygonPoint(290f, 10f),
            MushafPolygonPoint(290f, 40f),
            MushafPolygonPoint(60f, 40f)
        )
        val markerX = 70f
        val markerY = 24.70f

        val segments = QuranMushafGeometry.extractActiveVerseUnderlineSegments(
            polygon = polygon,
            markerX = markerX,
            markerY = markerY
        )

        assertEquals(1, segments.size)
        val seg = segments.first()
        assertEquals(1, seg.lineNumber)

        val expectedLeftNorm = QuranMushafGeometry.x343ToNorm(markerX + 10.5f)
        val rawPolyLeftNorm = QuranMushafGeometry.x343ToNorm(60f)
        assertEquals(expectedLeftNorm, seg.leftNorm, 0.0005f)
        assertTrue("Underline should start right of marker medallion", seg.leftNorm > rawPolyLeftNorm)
        assertTrue(seg.rightNorm > seg.leftNorm)
    }

    @Test
    fun `multi-line wrapped RTL verse produces per-line segments and excludes marker only on ending line`() {
        // Spans line 2 (yCenter=60.74) and line 3 (yCenter=96.78), marker on line 3 at x=120
        val polygon = listOf(
            MushafPolygonPoint(30f, 45f),
            MushafPolygonPoint(310f, 45f),
            MushafPolygonPoint(310f, 112f),
            MushafPolygonPoint(100f, 112f),
            MushafPolygonPoint(100f, 78f),
            MushafPolygonPoint(30f, 78f)
        )
        val markerX = 120f
        val markerY = 96.78f

        val segments = QuranMushafGeometry.extractActiveVerseUnderlineSegments(
            polygon = polygon,
            markerX = markerX,
            markerY = markerY
        )

        assertEquals(2, segments.size)
        val line2 = segments[0]
        val line3 = segments[1]

        assertEquals(2, line2.lineNumber)
        assertEquals(3, line3.lineNumber)

        // Line 2 has no marker, so it keeps the full left edge (30f)
        assertEquals(QuranMushafGeometry.x343ToNorm(30f), line2.leftNorm, 0.0005f)
        // Line 3 has the verse-end marker, so it trims to markerX + 10.5f
        assertEquals(QuranMushafGeometry.x343ToNorm(markerX + 10.5f), line3.leftNorm, 0.0005f)
        // Baselines strictly follow top-to-bottom order
        assertTrue(line3.baselineNorm > line2.baselineNorm)
    }

    @Test
    fun `degenerate polygon returns no segments`() {
        val segments = QuranMushafGeometry.extractActiveVerseUnderlineSegments(
            polygon = listOf(MushafPolygonPoint(10f, 10f), MushafPolygonPoint(20f, 20f)),
            markerX = null,
            markerY = null
        )
        assertTrue(segments.isEmpty())
    }
}
