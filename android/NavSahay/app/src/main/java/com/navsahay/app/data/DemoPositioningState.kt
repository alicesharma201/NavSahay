package com.navsahay.app.data

import android.graphics.Color

/**
 * Visual demo states representing the positioning mode across the 95-second timeline:
 * - 0.0s - 14.0s: NORMAL (GNSS Available, Approach)
 * - 14.0s - 48.0s: GNSS_LOST (Dead Reckoning Active, Tunnel Entry)
 * - 48.0s - 86.0s: CELLULAR_ASSISTED (Cellular Assistance Active, Mid-Tunnel)
 * - 86.0s - 91.0s: GNSS_RECOVERY (Position Correction Applied, Tunnel Exit)
 * - 91.0s - 95.0s: NORMAL (GNSS Available, Agra Road)
 *
 * NOTE: This is a frontend demo presentation layer driven by the physically consistent replay timeline.
 */
enum class PositioningPhase {
    NORMAL,
    GNSS_LOST,
    CELLULAR_ASSISTED,
    GNSS_RECOVERY
}

data class DemoPositioningState(
    val phase: PositioningPhase,
    val title: String,
    val subtitle: String,
    val eventText: String,
    val cardBgColor: Int,
    val cardStrokeColor: Int,
    val titleTextColor: Int,
    val isGnssActive: Boolean,
    val isImuActive: Boolean,
    val isCellularActive: Boolean,
    val visualConfidencePercent: Int
) {
    companion object {
        fun fromSample(timestamp: Double, uncertaintyMeters: Double): DemoPositioningState {
            val t = timestamp.coerceIn(0.0, GhatKiGuniRoute.TOTAL_DURATION_SEC)
            return when {
                t < GhatKiGuniRoute.TIME_ENTRY_SEC -> {
                    // Normal GNSS Approach (0s -> 14s)
                    val conf = 96
                    DemoPositioningState(
                        phase = PositioningPhase.NORMAL,
                        title = "🟢 GNSS AVAILABLE",
                        subtitle = "Positioning: GNSS + IMU Fusion",
                        eventText = "Normal multi-sensor navigation active",
                        cardBgColor = Color.parseColor("#ECFDF5"),
                        cardStrokeColor = Color.parseColor("#10B981"),
                        titleTextColor = Color.parseColor("#047857"),
                        isGnssActive = true,
                        isImuActive = true,
                        isCellularActive = false,
                        visualConfidencePercent = conf
                    )
                }
                t < GhatKiGuniRoute.TIME_CELLULAR_SEC -> {
                    // GNSS Outage / Dead Reckoning (14s -> 48s)
                    // Confidence visually degrades as uncertainty grows
                    val tFraction = ((t - GhatKiGuniRoute.TIME_ENTRY_SEC) / (GhatKiGuniRoute.TIME_CELLULAR_SEC - GhatKiGuniRoute.TIME_ENTRY_SEC)).coerceIn(0.0, 1.0)
                    val conf = (88 - (tFraction * 34)).toInt().coerceIn(52, 88)
                    DemoPositioningState(
                        phase = PositioningPhase.GNSS_LOST,
                        title = "🔴 GNSS SIGNAL LOST",
                        subtitle = "Dead Reckoning Active • Vehicle IMU",
                        eventText = "GNSS outage at West Portal • Kinematic DR active",
                        cardBgColor = Color.parseColor("#FEF2F2"),
                        cardStrokeColor = Color.parseColor("#EF4444"),
                        titleTextColor = Color.parseColor("#B91C1C"),
                        isGnssActive = false,
                        isImuActive = true,
                        isCellularActive = false,
                        visualConfidencePercent = conf
                    )
                }
                t < GhatKiGuniRoute.TIME_EXIT_SEC -> {
                    // Cellular Assisted (48s -> 86s)
                    // Confidence stabilizes with cellular constraints
                    val tFraction = ((t - GhatKiGuniRoute.TIME_CELLULAR_SEC) / (GhatKiGuniRoute.TIME_EXIT_SEC - GhatKiGuniRoute.TIME_CELLULAR_SEC)).coerceIn(0.0, 1.0)
                    val conf = (62 + (tFraction * 14)).toInt().coerceIn(60, 78)
                    DemoPositioningState(
                        phase = PositioningPhase.CELLULAR_ASSISTED,
                        title = "🟠 GNSS UNAVAILABLE",
                        subtitle = "Cellular Assistance Active • Drift Constrained",
                        eventText = "Cellular signals detected • Constraining position drift",
                        cardBgColor = Color.parseColor("#FFFBEB"),
                        cardStrokeColor = Color.parseColor("#F59E0B"),
                        titleTextColor = Color.parseColor("#B45309"),
                        isGnssActive = false,
                        isImuActive = true,
                        isCellularActive = true,
                        visualConfidencePercent = conf
                    )
                }
                t < GhatKiGuniRoute.TIME_RECOVERY_END_SEC -> {
                    // GNSS Recovery (86s -> 91s)
                    // Position correction applied at East Portal, uncertainty contracts
                    val conf = 92
                    DemoPositioningState(
                        phase = PositioningPhase.GNSS_RECOVERY,
                        title = "🔵 GNSS SIGNAL RECOVERED",
                        subtitle = "Position Correction Applied • Realignment Complete",
                        eventText = "GNSS fix restored at East Portal • Covariance converging",
                        cardBgColor = Color.parseColor("#EFF6FF"),
                        cardStrokeColor = Color.parseColor("#3B82F6"),
                        titleTextColor = Color.parseColor("#1D4ED8"),
                        isGnssActive = true,
                        isImuActive = true,
                        isCellularActive = true,
                        visualConfidencePercent = conf
                    )
                }
                else -> {
                    // Post-recovery Normal GNSS (91s -> 95s)
                    val conf = 96
                    DemoPositioningState(
                        phase = PositioningPhase.NORMAL,
                        title = "🟢 GNSS AVAILABLE",
                        subtitle = "Positioning: GNSS + IMU Fusion",
                        eventText = "Normal multi-sensor navigation resumed",
                        cardBgColor = Color.parseColor("#ECFDF5"),
                        cardStrokeColor = Color.parseColor("#10B981"),
                        titleTextColor = Color.parseColor("#047857"),
                        isGnssActive = true,
                        isImuActive = true,
                        isCellularActive = false,
                        visualConfidencePercent = conf
                    )
                }
            }
        }
    }
}
