package com.navsahay.app.data

import android.util.Log
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Normalized 2D coordinate representing a point on the 1024 x 576 satellite map image.
 * x: [0.0, 1.0] relative to 1024 width
 * y: [0.0, 1.0] relative to 576 height
 */
data class MapPoint(val x: Float, val y: Float)

data class RoutePosition(
    val normalizedX: Float,
    val normalizedY: Float,
    val headingRad: Float,
    val segmentIndex: Int,
    val isInsideTunnel: Boolean
)

/**
 * Source of Truth for the 53-point verified Ghat Ki Guni route and physically consistent timeline.
 */
object GhatKiGuniRoute {

    private const val TAG = "GhatKiGuniRoute"
    const val BITMAP_WIDTH = 1024f
    const val BITMAP_HEIGHT = 576f

    // Landmark indices
    const val INDEX_START = 0
    const val INDEX_WEST_PORTAL = 25   // Tunnel entrance (GNSS Lost)
    const val INDEX_EAST_PORTAL = 36   // Tunnel exit (GNSS Recovered)
    const val INDEX_DESTINATION = 52

    // Physically consistent timeline milestones (95-second total duration)
    // 858m tunnel traversed over 72s (14s to 86s) -> exactly 11.92 m/s = 42.9 km/h
    const val TOTAL_DURATION_SEC = 95.0
    const val TIME_ENTRY_SEC = 14.0
    const val TIME_CELLULAR_SEC = 48.0
    const val TIME_EXIT_SEC = 86.0
    const val TIME_RECOVERY_END_SEC = 91.0

    // 53 Normalized Route Points (relative to 1024x576)
    val points: List<MapPoint> = listOf(
        // --- SECTION 1: APPROACH ROAD (JAIPUR / WEST) ---
        MapPoint(0.1055f, 0.0000f), // 0: Start
        MapPoint(0.1035f, 0.0347f), // 1
        MapPoint(0.0996f, 0.0781f), // 2
        MapPoint(0.0938f, 0.1302f), // 3
        MapPoint(0.0898f, 0.1701f), // 4
        MapPoint(0.0859f, 0.2049f), // 5
        MapPoint(0.0938f, 0.2188f), // 6
        MapPoint(0.1094f, 0.2257f), // 7
        MapPoint(0.1309f, 0.2326f), // 8
        MapPoint(0.1523f, 0.2465f), // 9
        MapPoint(0.1738f, 0.2674f), // 10
        MapPoint(0.1934f, 0.2951f), // 11
        MapPoint(0.2109f, 0.3333f), // 12
        MapPoint(0.2227f, 0.3785f), // 13
        MapPoint(0.2305f, 0.4271f), // 14
        MapPoint(0.2344f, 0.4722f), // 15
        MapPoint(0.2363f, 0.5000f), // 16: Toll Plaza
        MapPoint(0.2383f, 0.5312f), // 17
        MapPoint(0.2422f, 0.5590f), // 18
        MapPoint(0.2520f, 0.5833f), // 19
        MapPoint(0.2676f, 0.5972f), // 20
        MapPoint(0.2891f, 0.6042f), // 21
        MapPoint(0.3145f, 0.6042f), // 22
        MapPoint(0.3379f, 0.5990f), // 23
        MapPoint(0.3555f, 0.5938f), // 24
        MapPoint(0.3633f, 0.5903f), // 25: Tunnel West Portal (Entrance)

        // --- SECTION 2: GHAT KI GUNI TUNNEL (NH 21 - 858m) ---
        MapPoint(0.3926f, 0.6100f), // 26
        MapPoint(0.4219f, 0.6297f), // 27
        MapPoint(0.4512f, 0.6494f), // 28
        MapPoint(0.4805f, 0.6691f), // 29
        MapPoint(0.5098f, 0.6888f), // 30
        MapPoint(0.5391f, 0.7085f), // 31
        MapPoint(0.5684f, 0.7282f), // 32
        MapPoint(0.5977f, 0.7479f), // 33
        MapPoint(0.6270f, 0.7676f), // 34
        MapPoint(0.6562f, 0.7873f), // 35
        MapPoint(0.6855f, 0.8073f), // 36: Tunnel East Portal (Exit)

        // --- SECTION 3: EXIT ROAD (AGRA ROAD / EAST) ---
        MapPoint(0.6973f, 0.8229f), // 37
        MapPoint(0.7129f, 0.8368f), // 38
        MapPoint(0.7305f, 0.8368f), // 39
        MapPoint(0.7461f, 0.8194f), // 40
        MapPoint(0.7578f, 0.7917f), // 41
        MapPoint(0.7695f, 0.7604f), // 42
        MapPoint(0.7852f, 0.7361f), // 43
        MapPoint(0.8047f, 0.7118f), // 44
        MapPoint(0.8281f, 0.6840f), // 45
        MapPoint(0.8516f, 0.6562f), // 46
        MapPoint(0.8770f, 0.6354f), // 47
        MapPoint(0.9043f, 0.6215f), // 48
        MapPoint(0.9316f, 0.6215f), // 49
        MapPoint(0.9590f, 0.6319f), // 50
        MapPoint(0.9844f, 0.6458f), // 51
        MapPoint(0.9990f, 0.6562f)  // 52: Destination (Agra Road)
    )

    // Cumulative pixel distances along the route
    private val segmentPixelLengths: FloatArray
    private val cumulativePixelDistances: FloatArray
    val totalRoutePixelLength: Float

    // Route progress fractions for key landmarks
    val progressWestPortal: Float
    val progressEastPortal: Float

    init {
        // 1. Automated Sanity Check
        require(points.size == 53) { "Sanity check failed: route must contain exactly 53 points, found ${points.size}" }
        require(INDEX_WEST_PORTAL < INDEX_EAST_PORTAL) { "Sanity check failed: West Portal must precede East Portal" }

        for ((idx, pt) in points.withIndex()) {
            require(pt.x in 0.0f..1.0f) { "Sanity check failed: point $idx x (${pt.x}) not in [0, 1]" }
            require(pt.y in 0.0f..1.0f) { "Sanity check failed: point $idx y (${pt.y}) not in [0, 1]" }
        }

        // 2. Precompute segment lengths and cumulative distances in unscaled bitmap pixel space
        segmentPixelLengths = FloatArray(points.size - 1)
        cumulativePixelDistances = FloatArray(points.size)
        cumulativePixelDistances[0] = 0f

        var accum = 0f
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val dx = (p1.x - p0.x) * BITMAP_WIDTH
            val dy = (p1.y - p0.y) * BITMAP_HEIGHT
            val dist = hypot(dx, dy)
            segmentPixelLengths[i] = dist
            accum += dist
            cumulativePixelDistances[i + 1] = accum
        }

        totalRoutePixelLength = accum
        progressWestPortal = cumulativePixelDistances[INDEX_WEST_PORTAL] / totalRoutePixelLength
        progressEastPortal = cumulativePixelDistances[INDEX_EAST_PORTAL] / totalRoutePixelLength

        Log.i(TAG, "GhatKiGuniRoute initialized: 53 points, total length = ${totalRoutePixelLength.toInt()} px")
        Log.i(TAG, "West Portal progress = ${(progressWestPortal * 100).toInt()}% (index $INDEX_WEST_PORTAL)")
        Log.i(TAG, "East Portal progress = ${(progressEastPortal * 100).toInt()}% (index $INDEX_EAST_PORTAL)")
    }

    /**
     * Maps replay elapsed time (0.0s to TOTAL_DURATION_SEC) to continuous route progress [0.0, 1.0],
     * synchronizing GNSS outage strictly with the tunnel entrance (t=14s) and exit (t=86s).
     */
    fun getProgressForTime(elapsedSec: Double, totalSec: Double = TOTAL_DURATION_SEC): Float {
        val t = elapsedSec.coerceIn(0.0, totalSec)
        return when {
            t <= TIME_ENTRY_SEC -> {
                // Approach section (0s -> 14s): maps to 0.0 -> progressWestPortal
                val fraction = (t / TIME_ENTRY_SEC).toFloat()
                fraction * progressWestPortal
            }
            t <= TIME_EXIT_SEC -> {
                // Tunnel section (14s -> 86s, 72s duration): maps to progressWestPortal -> progressEastPortal
                val fraction = ((t - TIME_ENTRY_SEC) / (TIME_EXIT_SEC - TIME_ENTRY_SEC)).toFloat()
                progressWestPortal + fraction * (progressEastPortal - progressWestPortal)
            }
            else -> {
                // Exit section (86s -> totalSec): maps to progressEastPortal -> 1.0
                val remainingTotal = (totalSec - TIME_EXIT_SEC).coerceAtLeast(0.1)
                val fraction = ((t - TIME_EXIT_SEC) / remainingTotal).toFloat().coerceIn(0f, 1f)
                progressEastPortal + fraction * (1f - progressEastPortal)
            }
        }.coerceIn(0f, 1f)
    }

    /**
     * Interpolates exact normalized position and heading along the route for a given progress in [0.0, 1.0].
     */
    fun getPositionAtProgress(progress: Float): RoutePosition {
        val p = progress.coerceIn(0f, 1f)
        val targetDist = p * totalRoutePixelLength

        var segIndex = 0
        for (i in 0 until points.size - 1) {
            if (targetDist <= cumulativePixelDistances[i + 1] || i == points.size - 2) {
                segIndex = i
                break
            }
        }

        val segStartDist = cumulativePixelDistances[segIndex]
        val segLength = segmentPixelLengths[segIndex].coerceAtLeast(0.001f)
        val segFraction = ((targetDist - segStartDist) / segLength).coerceIn(0f, 1f)

        val p0 = points[segIndex]
        val p1 = points[segIndex + 1]

        val normX = p0.x + segFraction * (p1.x - p0.x)
        val normY = p0.y + segFraction * (p1.y - p0.y)

        val dx = (p1.x - p0.x) * BITMAP_WIDTH
        val dy = (p1.y - p0.y) * BITMAP_HEIGHT
        val heading = atan2(dy, dx)

        val isInsideTunnel = p >= progressWestPortal && p <= progressEastPortal

        return RoutePosition(
            normalizedX = normX,
            normalizedY = normY,
            headingRad = heading,
            segmentIndex = segIndex,
            isInsideTunnel = isInsideTunnel
        )
    }
}
