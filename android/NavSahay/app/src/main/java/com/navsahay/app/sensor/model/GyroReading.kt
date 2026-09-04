package com.navsahay.app.sensor.model

/**
 * Immutable container for raw 3-axis gyroscope measurement.
 *
 * @property timestampNs Canonical sensor event timestamp in nanoseconds (SensorEvent.timestamp).
 * @property gx Angular velocity around X axis (rad/s).
 * @property gy Angular velocity around Y axis (rad/s).
 * @property gz Angular velocity around Z axis (rad/s).
 */
data class GyroReading(
    val timestampNs: Long,
    val gx: Float,
    val gy: Float,
    val gz: Float
)
