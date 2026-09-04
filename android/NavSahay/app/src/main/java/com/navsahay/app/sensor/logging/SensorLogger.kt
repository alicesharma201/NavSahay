package com.navsahay.app.sensor.logging

import android.util.Log
import com.navsahay.app.sensor.model.AccelReading
import com.navsahay.app.sensor.model.GnssReading
import com.navsahay.app.sensor.model.GyroReading
import com.navsahay.app.sensor.model.SensorDiagnostics

/**
 * Lightweight diagnostic logger for tracking sensor rates, event counts, and interval gaps.
 */
class SensorLogger {

    companion object {
        private const val TAG = "NavSahaySensor"
        private const val GAP_THRESHOLD_NS = 200_000_000L // 200ms gap considered an unexpected interruption for 50-100Hz sensors
    }

    private var lastAccelTimestampNs = 0L
    private var lastGyroTimestampNs = 0L
    private var accelGapCount = 0L
    private var gyroGapCount = 0L

    fun logAccel(reading: AccelReading) {
        if (lastAccelTimestampNs > 0L) {
            val delta = reading.timestampNs - lastAccelTimestampNs
            if (delta > GAP_THRESHOLD_NS) {
                accelGapCount++
                Log.w(TAG, "Accel interval gap detected: ${delta / 1_000_000} ms")
            }
        }
        lastAccelTimestampNs = reading.timestampNs
    }

    fun logGyro(reading: GyroReading) {
        if (lastGyroTimestampNs > 0L) {
            val delta = reading.timestampNs - lastGyroTimestampNs
            if (delta > GAP_THRESHOLD_NS) {
                gyroGapCount++
                Log.w(TAG, "Gyro interval gap detected: ${delta / 1_000_000} ms")
            }
        }
        lastGyroTimestampNs = reading.timestampNs
    }

    fun logGnss(reading: GnssReading) {
        Log.i(TAG, "GNSS Fix: lat=${reading.latitude}, lon=${reading.longitude}, acc=±${reading.accuracyMeters}m, speed=${reading.speedMps ?: 0f}m/s")
    }

    fun logDiagnostics(diag: SensorDiagnostics) {
        Log.d(TAG, "Diagnostics: GNSS(avail=${diag.isGnssAvailable}, acc=${diag.gnssAccuracyMeters}m, n=${diag.gnssEventCount}) | Accel(active=${diag.isAccelActive}, rate=${String.format("%.1f", diag.accelObservedHz)}Hz, n=${diag.accelEventCount}, gaps=$accelGapCount) | Gyro(active=${diag.isGyroActive}, rate=${String.format("%.1f", diag.gyroObservedHz)}Hz, n=${diag.gyroEventCount}, gaps=$gyroGapCount)")
    }

    fun reset() {
        lastAccelTimestampNs = 0L
        lastGyroTimestampNs = 0L
        accelGapCount = 0L
        gyroGapCount = 0L
    }
}
