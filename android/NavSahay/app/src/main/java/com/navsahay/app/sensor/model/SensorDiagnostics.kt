package com.navsahay.app.sensor.model

/**
 * Diagnostic snapshot of live sensor acquisition rates, state, and latest samples.
 */
data class SensorDiagnostics(
    val isGnssAvailable: Boolean,
    val gnssAccuracyMeters: Float?,
    val gnssEventCount: Long,
    val isAccelActive: Boolean,
    val accelObservedHz: Double,
    val latestAccelAx: Float?,
    val latestAccelAy: Float?,
    val latestAccelAz: Float?,
    val accelEventCount: Long,
    val isGyroActive: Boolean,
    val gyroObservedHz: Double,
    val latestGyroGx: Float?,
    val latestGyroGy: Float?,
    val latestGyroGz: Float?,
    val gyroEventCount: Long
)
