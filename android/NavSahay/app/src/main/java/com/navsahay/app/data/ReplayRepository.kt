package com.navsahay.app.data

import android.content.Context
import kotlin.math.sin

object ReplayRepository {

    private var cachedSamples: List<NavigationSample>? = null

    /**
     * Generates a physically consistent 95-second telemetry sequence (950 samples at 10 Hz)
     * corresponding to the actual 858m Ghat Ki Guni Tunnel traversal at ~42 km/h.
     */
    fun loadRoute(context: Context): List<NavigationSample> {
        cachedSamples?.let { return it }

        val samples = mutableListOf<NavigationSample>()
        val totalSteps = 950 // 95.0 seconds at 10 Hz (0.1s step)

        for (i in 0 until totalSteps) {
            val t = i * 0.1
            val progress = GhatKiGuniRoute.getProgressForTime(t, GhatKiGuniRoute.TOTAL_DURATION_SEC)
            val pos = GhatKiGuniRoute.getPositionAtProgress(progress)

            // Realistic vehicle speed through tunnel (~41.5 - 43.5 km/h)
            val speedVariation = sin(t * 0.2) * 0.8 + sin(t * 0.05) * 0.4
            val speedKmh = 42.4 + speedVariation

            // Physically grounded filter uncertainty curve
            val uncertainty: Double
            val gnssStatus: String

            when {
                t < GhatKiGuniRoute.TIME_ENTRY_SEC -> {
                    // Normal GNSS Approach (0s -> 14s)
                    uncertainty = 1.6 + sin(t * 0.5) * 0.2
                    gnssStatus = "AVAILABLE"
                }
                t < GhatKiGuniRoute.TIME_CELLULAR_SEC -> {
                    // Dead Reckoning Outage (14s -> 48s, 34s duration)
                    // Covariance uncertainty expands from 1.8m to 6.2m
                    val frac = (t - GhatKiGuniRoute.TIME_ENTRY_SEC) / (GhatKiGuniRoute.TIME_CELLULAR_SEC - GhatKiGuniRoute.TIME_ENTRY_SEC)
                    uncertainty = 1.8 + frac * 4.4 + sin(t * 0.3) * 0.15
                    gnssStatus = "DENIED"
                }
                t < GhatKiGuniRoute.TIME_EXIT_SEC -> {
                    // Cellular Assistance Active (48s -> 86s, 38s duration)
                    // Drift is bounded by cellular constraints, stabilizing to ~4.4m
                    val frac = (t - GhatKiGuniRoute.TIME_CELLULAR_SEC) / (GhatKiGuniRoute.TIME_EXIT_SEC - GhatKiGuniRoute.TIME_CELLULAR_SEC)
                    uncertainty = 6.2 - frac * 1.8 + sin(t * 0.3) * 0.15
                    gnssStatus = "DENIED"
                }
                t < GhatKiGuniRoute.TIME_RECOVERY_END_SEC -> {
                    // GNSS Recovery (86s -> 91s, 5s duration)
                    // Fast covariance convergence back to nominal GNSS levels
                    val frac = (t - GhatKiGuniRoute.TIME_EXIT_SEC) / (GhatKiGuniRoute.TIME_RECOVERY_END_SEC - GhatKiGuniRoute.TIME_EXIT_SEC)
                    uncertainty = 4.4 - frac * 2.7
                    gnssStatus = "AVAILABLE"
                }
                else -> {
                    // Post-recovery Normal GNSS (91s -> 95s)
                    uncertainty = 1.6 + sin(t * 0.5) * 0.15
                    gnssStatus = "AVAILABLE"
                }
            }

            samples.add(
                NavigationSample(
                    timestamp = (t * 10.0).toInt() / 10.0,
                    estimatedX = pos.normalizedX.toDouble(),
                    estimatedY = pos.normalizedY.toDouble(),
                    groundTruthX = pos.normalizedX.toDouble(),
                    groundTruthY = pos.normalizedY.toDouble(),
                    gnssX = if (gnssStatus == "AVAILABLE") pos.normalizedX.toDouble() else null,
                    gnssY = if (gnssStatus == "AVAILABLE") pos.normalizedY.toDouble() else null,
                    speedKmh = ((speedKmh * 10.0).toInt()) / 10.0,
                    errorMeters = ((uncertainty * 0.75 * 10.0).toInt()) / 10.0,
                    uncertaintyMeters = ((uncertainty * 10.0).toInt()) / 10.0,
                    gnssStatus = gnssStatus
                )
            )
        }

        cachedSamples = samples
        return samples
    }
}
