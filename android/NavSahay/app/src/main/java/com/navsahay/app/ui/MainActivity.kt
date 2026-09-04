package com.navsahay.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.navsahay.app.R
import com.navsahay.app.data.DemoPositioningState
import com.navsahay.app.data.NavigationSample
import com.navsahay.app.data.ReplayRepository
import com.navsahay.app.engine.PlaybackState
import com.navsahay.app.engine.ReplayController
import com.navsahay.app.engine.ReplayListener
import com.navsahay.app.sensor.SensorAcquisitionManager

class MainActivity : AppCompatActivity(), ReplayListener {

    private lateinit var mapView: TrajectoryMapView
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvUncertainty: TextView
    private lateinit var tvEventTicker: TextView
    private lateinit var layoutSourceGnss: LinearLayout
    private lateinit var tvSourceGnss: TextView
    private lateinit var layoutSourceImu: LinearLayout
    private lateinit var tvSourceImu: TextView
    private lateinit var layoutSourceCellular: LinearLayout
    private lateinit var tvSourceCellular: TextView
    private lateinit var tvTimeline: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button

    private var replayController: ReplayController? = null
    private var samples: List<NavigationSample> = emptyList()

    // Live Sensor Acquisition Manager (Active in background)
    private lateinit var sensorAcquisitionManager: SensorAcquisitionManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            Log.d("MainActivity", "Location permission granted. Starting sensors.")
            sensorAcquisitionManager.start()
        } else {
            Log.w("MainActivity", "Location permission denied. Proceeding without location access.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadData()
        setupListeners()
        setupSensors()

        checkAndRequestPermissions()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.trajectoryMapView)
        statusCard = findViewById(R.id.cardStatus)
        statusTitle = findViewById(R.id.tvStatusTitle)
        statusSubtitle = findViewById(R.id.tvStatusSubtitle)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvConfidence = findViewById(R.id.tvConfidence)
        tvUncertainty = findViewById(R.id.tvUncertainty)
        tvEventTicker = findViewById(R.id.tvEventTicker)
        layoutSourceGnss = findViewById(R.id.layoutSourceGnss)
        tvSourceGnss = findViewById(R.id.tvSourceGnss)
        layoutSourceImu = findViewById(R.id.layoutSourceImu)
        tvSourceImu = findViewById(R.id.tvSourceImu)
        layoutSourceCellular = findViewById(R.id.layoutSourceCellular)
        tvSourceCellular = findViewById(R.id.tvSourceCellular)
        tvTimeline = findViewById(R.id.tvTimeline)
        progressBar = findViewById(R.id.progressBar)
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnReset = findViewById(R.id.btnReset)
    }

    private fun loadData() {
        samples = ReplayRepository.loadRoute(this)
        mapView.setRouteData(samples)
        progressBar.max = maxOf(1, samples.size - 1)

        replayController = ReplayController(samples, this)
        if (samples.isNotEmpty()) {
            updateTelemetry(samples[0], 0)
        }
    }

    private fun setupListeners() {
        btnStart.setOnClickListener {
            replayController?.start()
        }
        btnPause.setOnClickListener {
            replayController?.pause()
        }
        btnReset.setOnClickListener {
            replayController?.reset()
            mapView.reset()
            if (samples.isNotEmpty()) {
                updateTelemetry(samples[0], 0)
            }
        }
    }

    private fun setupSensors() {
        sensorAcquisitionManager = SensorAcquisitionManager(this)
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermissions()) {
            sensorAcquisitionManager.start()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorAcquisitionManager.stop()
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions() {
        if (hasLocationPermissions()) {
            Log.d("MainActivity", "Location permission already granted.")
            sensorAcquisitionManager.start()
        } else {
            Log.d("MainActivity", "Requesting location permissions.")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onSampleDispatched(sample: NavigationSample, sampleIndex: Int, totalSamples: Int) {
        runOnUiThread {
            updateTelemetry(sample, sampleIndex)
            mapView.setCurrentSample(sample, sampleIndex)
        }
    }

    override fun onStateChanged(state: PlaybackState) {
        runOnUiThread {
            when (state) {
                PlaybackState.PLAYING -> {
                    btnStart.isEnabled = false
                    btnPause.isEnabled = true
                    btnReset.isEnabled = true
                }
                PlaybackState.PAUSED -> {
                    btnStart.isEnabled = true
                    btnPause.isEnabled = false
                    btnReset.isEnabled = true
                }
                PlaybackState.IDLE -> {
                    btnStart.isEnabled = true
                    btnPause.isEnabled = false
                    btnReset.isEnabled = false
                }
                PlaybackState.COMPLETED -> {
                    btnStart.isEnabled = false
                    btnPause.isEnabled = false
                    btnReset.isEnabled = true
                }
            }
        }
    }

    private fun updateTelemetry(sample: NavigationSample, index: Int) {
        val state = DemoPositioningState.fromSample(sample.timestamp, sample.uncertaintyMeters)

        // 1. Positioning Status Card
        statusCard.setCardBackgroundColor(state.cardBgColor)
        statusCard.strokeColor = state.cardStrokeColor
        statusTitle.text = state.title
        statusTitle.setTextColor(state.titleTextColor)
        statusSubtitle.text = state.subtitle

        // 2. Active Sources
        updateSourcePill(layoutSourceGnss, tvSourceGnss, "GNSS", state.isGnssActive, Color.parseColor("#047857"))
        updateSourcePill(layoutSourceImu, tvSourceImu, "IMU", state.isImuActive, Color.parseColor("#0284C7"))
        updateSourcePill(layoutSourceCellular, tvSourceCellular, "CELLULAR", state.isCellularActive, Color.parseColor("#B45309"))

        // 3. Speed & Confidence
        tvSpeed.text = String.format("%.1f km/h", sample.speedKmh)
        tvConfidence.text = "${state.visualConfidencePercent}%"
        tvConfidence.setTextColor(state.titleTextColor)
        tvUncertainty.text = String.format("(±%.1f m)", sample.uncertaintyMeters)

        // 4. System Event Ticker
        tvEventTicker.text = "ℹ️ ${state.eventText}"

        // 5. Timeline & Progress
        val totalTime = if (samples.isNotEmpty()) samples.last().timestamp else 24.9
        tvTimeline.text = String.format("t = %.1f s / %.1f s", sample.timestamp, totalTime)
        progressBar.progress = index
    }

    private fun updateSourcePill(
        layout: LinearLayout,
        textView: TextView,
        name: String,
        isActive: Boolean,
        activeTextColor: Int
    ) {
        if (isActive) {
            layout.setBackgroundResource(R.drawable.pill_source_active)
            textView.text = "● $name"
            textView.setTextColor(activeTextColor)
        } else {
            layout.setBackgroundResource(R.drawable.pill_source_inactive)
            textView.text = "○ $name"
            textView.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        replayController?.release()
        sensorAcquisitionManager.stop()
    }
}
