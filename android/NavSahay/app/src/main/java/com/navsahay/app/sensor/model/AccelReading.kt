package com.navsahay.app.sensor.model

/**
 * Immutable container for raw 3-axis accelerometer measurement.
 *
 * @property timestampNs Canonical sensor event timestamp in nanoseconds (SensorEvent.timestamp).
 * @property ax Acceleration in X axis (m/s^2), including gravity.
 * @property ay Acceleration in Y axis (m/s^2), including gravity.
 * @property az Acceleration in Z axis (m/s^2), including gravity.
 */
data class AccelReading(
    val timestampNs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float
)
