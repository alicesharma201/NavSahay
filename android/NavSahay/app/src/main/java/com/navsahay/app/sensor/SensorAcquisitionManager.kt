package com.navsahay.app.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import com.navsahay.app.sensor.logging.SensorLogger
import com.navsahay.app.sensor.model.AccelReading
import com.navsahay.app.sensor.model.GnssReading
import com.navsahay.app.sensor.model.GyroReading
import com.navsahay.app.sensor.model.SensorDiagnostics

/**
 * Central lifecycle-aware manager for acquiring live Android GNSS, accelerometer, and gyroscope streams.
 *
 * Owns:
 * - SensorManager & listeners
 * - LocationManager & GNSS callbacks
 * - Observed rate calculation (Hz)
 * - Event dispatching & diagnostic snapshots
 *
 * Completely decoupled from Android UI Views.
 */
class SensorAcquisitionManager(context: Context) : SensorEventListener, LocationListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val logger = SensorLogger()

    @Volatile
    private var isAcquiring = false

    // Rate calculation state
    private var accelEventCount = 0L
    private var gyroEventCount = 0L
    private var gnssEventCount = 0L

    private var accelWindowCount = 0
    private var gyroWindowCount = 0
    private var lastRateCalcRealtimeMs = 0L
    private var observedAccelHz = 0.0
    private var observedGyroHz = 0.0

    // Latest readings cache
    @Volatile private var latestAccel: AccelReading? = null
    @Volatile private var latestGyro: GyroReading? = null
    @Volatile private var latestGnss: GnssReading? = null
    @Volatile private var isGnssAvailable = false

    // Listeners
    var onAccelReading: ((AccelReading) -> Unit)? = null
    var onGyroReading: ((GyroReading) -> Unit)? = null
    var onGnssReading: ((GnssReading) -> Unit)? = null
    var onDiagnosticsUpdate: ((SensorDiagnostics) -> Unit)? = null

    /**
     * Start live sensor and GNSS acquisition. Idempotent.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isAcquiring) return
        isAcquiring = true

        accelEventCount = 0L
        gyroEventCount = 0L
        gnssEventCount = 0L
        accelWindowCount = 0
        gyroWindowCount = 0
        lastRateCalcRealtimeMs = SystemClock.elapsedRealtime()
        observedAccelHz = 0.0
        observedGyroHz = 0.0
        logger.reset()

        // Register Accelerometer
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Register Gyroscope
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Register GNSS / LocationManager
        try {
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    100L,   // minTimeMs: request frequent updates
                    0.0f,   // minDistanceM: report all movement
                    this
                )
                isGnssAvailable = true
            } else {
                isGnssAvailable = false
            }
        } catch (e: SecurityException) {
            isGnssAvailable = false
        } catch (e: Exception) {
            isGnssAvailable = false
        }

        dispatchDiagnostics()
    }

    /**
     * Stop live sensor and GNSS acquisition and unregister listeners. Idempotent.
     */
    fun stop() {
        if (!isAcquiring) return
        isAcquiring = false

        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            // Safe removal
        }

        dispatchDiagnostics()
    }

    fun isRunning(): Boolean = isAcquiring

    // --- SensorEventListener Implementation ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isAcquiring || event == null) return

        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelEventCount++
                accelWindowCount++
                val reading = AccelReading(
                    timestampNs = event.timestamp,
                    ax = event.values[0],
                    ay = event.values[1],
                    az = event.values[2]
                )
                latestAccel = reading
                logger.logAccel(reading)
                onAccelReading?.invoke(reading)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroEventCount++
                gyroWindowCount++
                val reading = GyroReading(
                    timestampNs = event.timestamp,
                    gx = event.values[0],
                    gy = event.values[1],
                    gz = event.values[2]
                )
                latestGyro = reading
                logger.logGyro(reading)
                onGyroReading?.invoke(reading)
            }
        }

        // Update observed rates every 1000ms
        val elapsed = now - lastRateCalcRealtimeMs
        if (elapsed >= 1000L) {
            observedAccelHz = (accelWindowCount * 1000.0) / elapsed
            observedGyroHz = (gyroWindowCount * 1000.0) / elapsed
            accelWindowCount = 0
            gyroWindowCount = 0
            lastRateCalcRealtimeMs = now

            val diag = createDiagnosticsSnapshot()
            logger.logDiagnostics(diag)
            onDiagnosticsUpdate?.invoke(diag)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for standard IMU stream
    }

    // --- LocationListener Implementation ---

    override fun onLocationChanged(location: Location) {
        if (!isAcquiring) return
        gnssEventCount++
        isGnssAvailable = true

        val reading = GnssReading(
            elapsedRealtimeNs = location.elapsedRealtimeNanos,
            timeUtcMs = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0.0f,
            speedMps = if (location.hasSpeed()) location.speed else null,
            bearingDegrees = if (location.hasBearing()) location.bearing else null,
            isAvailable = true
        )
        latestGnss = reading
        logger.logGnss(reading)
        onGnssReading?.invoke(reading)
        dispatchDiagnostics()
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            isGnssAvailable = true
            dispatchDiagnostics()
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            isGnssAvailable = false
            dispatchDiagnostics()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Compatibility handler
    }

    private fun dispatchDiagnostics() {
        val diag = createDiagnosticsSnapshot()
        onDiagnosticsUpdate?.invoke(diag)
    }

    private fun createDiagnosticsSnapshot(): SensorDiagnostics {
        val currAccel = latestAccel
        val currGyro = latestGyro
        val currGnss = latestGnss

        return SensorDiagnostics(
            isGnssAvailable = isGnssAvailable && currGnss != null,
            gnssAccuracyMeters = currGnss?.accuracyMeters,
            gnssEventCount = gnssEventCount,
            isAccelActive = isAcquiring && accelerometer != null,
            accelObservedHz = observedAccelHz,
            latestAccelAx = currAccel?.ax,
            latestAccelAy = currAccel?.ay,
            latestAccelAz = currAccel?.az,
            accelEventCount = accelEventCount,
            isGyroActive = isAcquiring && gyroscope != null,
            gyroObservedHz = observedGyroHz,
            latestGyroGx = currGyro?.gx,
            latestGyroGy = currGyro?.gy,
            latestGyroGz = currGyro?.gz,
            gyroEventCount = gyroEventCount
        )
    }
}
