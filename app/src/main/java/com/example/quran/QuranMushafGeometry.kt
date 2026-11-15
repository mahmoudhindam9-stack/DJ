package com.example.quran

import kotlin.math.abs

internal data class MushafPolygonPoint(val x: Float, val y: Float)

internal data class VerseUnderlineSegment(
    val lineNumber: Int,
    val leftNorm: Float,
    val rightNorm: Float,
    val baselineNorm: Float
)

internal object QuranMushafGeometry {
    private const val LINE_FIRST_CENTER_550 = 24.70f
    private const val LINE_STEP_550 = 36.04f

    fun x343ToNorm(x343: Float): Float {
        val imgX1024 = 51f + (x343 / 343f) * 915f
        return ((imgX1024 - 40f) / 938f).coerceIn(0f, 1f)
    }

    fun y550ToNorm(y550: Float): Float {
        val imgY1656 = (y550 / 550f) * 1620f - 1f
        return ((imgY1656 - 14f) / 1610f).coerceIn(0f, 1f)
    }

    /**
     * Converts a verse polygon in 343x550 Mushaf coordinate space into clean per-line
     * horizontal underline segments (in normalized 0..1 cropped canvas coordinates).
     * Excludes the verse-end marker medallion so only the recited Arabic verse text is underlined.
     */
    fun extractActiveVerseUnderlineSegments(
        polygon: List<MushafPolygonPoint>,
        markerX: Float?,
        markerY: Float?
    ): List<VerseUnderlineSegment> {
        if (polygon.size < 3) return emptyList()

        val segments = mutableListOf<VerseUnderlineSegment>()
        for (lineNumber in 1..15) {
            val yCenter = LINE_FIRST_CENTER_550 + (lineNumber - 1) * LINE_STEP_550
            val scanYs = floatArrayOf(yCenter - 6f, yCenter, yCenter + 6f)

            var minHitX = Float.MAX_VALUE
            var maxHitX = -Float.MAX_VALUE
            var hasLineIntersection = false

            for (scanY in scanYs) {
                val crossings = scanlineIntersectionsX(polygon, scanY)
                if (crossings.size >= 2) {
                    hasLineIntersection = true
                    val lineMin = crossings.minOrNull() ?: continue
                    val lineMax = crossings.maxOrNull() ?: continue
                    if (lineMin < minHitX) minHitX = lineMin
                    if (lineMax > maxHitX) maxHitX = lineMax
                }
            }

            if (!hasLineIntersection || maxHitX - minHitX < 6f) continue

            var adjustedLeft343 = minHitX.coerceIn(0f, 343f)
            val adjustedRight343 = maxHitX.coerceIn(0f, 343f)

            // If the verse ends on this line, trim the left edge before the ornate verse marker
            if (markerX != null && markerY != null && abs(markerY - yCenter) <= 18.5f) {
                val markerRightEdge = markerX + 10.5f
                if (adjustedLeft343 <= markerRightEdge && adjustedRight343 - markerRightEdge >= 8f) {
                    adjustedLeft343 = markerRightEdge
                }
            }

            if (adjustedRight343 - adjustedLeft343 < 6f) continue

            // Place the underline cleanly beneath the calligraphy baseline without crossing diacritics
            val polyLineBottomCandidates = polygon
                .map { it.y }
                .filter { it in (yCenter + 8f)..(yCenter + 19f) }
            val underlineY550 = (polyLineBottomCandidates.maxOrNull() ?: (yCenter + 15.0f))
                .coerceIn(yCenter + 12.0f, yCenter + 16.5f)
                .coerceAtMost(547f)

            val leftNorm = x343ToNorm(adjustedLeft343)
            val rightNorm = x343ToNorm(adjustedRight343)
            if (rightNorm > leftNorm) {
                segments.add(
                    VerseUnderlineSegment(
                        lineNumber = lineNumber,
                        leftNorm = leftNorm,
                        rightNorm = rightNorm,
                        baselineNorm = y550ToNorm(underlineY550)
                    )
                )
            }
        }
        return segments
    }

    private fun scanlineIntersectionsX(polygon: List<MushafPolygonPoint>, y: Float): List<Float> {
        val xs = mutableListOf<Float>()
        val n = polygon.size
        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]
            val crosses = (p1.y <= y && p2.y > y) || (p2.y <= y && p1.y > y)
            if (crosses && p2.y != p1.y) {
                val t = (y - p1.y) / (p2.y - p1.y)
                xs.add(p1.x + t * (p2.x - p1.x))
            }
        }
        return xs
    }
}
