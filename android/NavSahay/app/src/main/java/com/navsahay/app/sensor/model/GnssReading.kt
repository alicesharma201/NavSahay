package com.navsahay.app.sensor.model

/**
 * Immutable container for GNSS / GPS position fix.
 *
 * @property elapsedRealtimeNs Canonical synchronization timestamp from Location.elapsedRealtimeNanos.
 * @property timeUtcMs UTC timestamp in milliseconds for human-readable logging and timeline alignment.
 * @property latitude WGS84 latitude in degrees.
 * @property longitude WGS84 longitude in degrees.
 * @property altitude WGS84 altitude in meters (null if unavailable).
 * @property accuracyMeters Estimated 1-sigma horizontal accuracy in meters.
 * @property speedMps Ground speed in m/s (null if unavailable).
 * @property bearingDegrees Bearing in degrees [0, 360) (null if unavailable).
 * @property isAvailable Whether the GNSS provider currently delivers valid fixes.
 */
data class GnssReading(
    val elapsedRealtimeNs: Long,
    val timeUtcMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val isAvailable: Boolean
)
