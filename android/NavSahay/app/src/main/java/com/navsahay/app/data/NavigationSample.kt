package com.navsahay.app.data

/**
 * Telemetry data sample from verified IO-VNBD Python experiment.
 */
data class NavigationSample(
    val timestamp: Double,
    val estimatedX: Double,
    val estimatedY: Double,
    val groundTruthX: Double,
    val groundTruthY: Double,
    val gnssX: Double?,
    val gnssY: Double?,
    val speedKmh: Double,
    val errorMeters: Double,
    val uncertaintyMeters: Double,
    val gnssStatus: String
) {
    val isGnssDenied: Boolean
        get() = gnssStatus.equals("DENIED", ignoreCase = true)
}
