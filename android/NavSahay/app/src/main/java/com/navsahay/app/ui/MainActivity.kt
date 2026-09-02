package com.navsahay.app.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.navsahay.app.R
import com.navsahay.app.data.NavigationSample
import com.navsahay.app.data.ReplayRepository
import com.navsahay.app.engine.PlaybackState
import com.navsahay.app.engine.ReplayController
import com.navsahay.app.engine.ReplayListener

class MainActivity : AppCompatActivity(), ReplayListener {

    private lateinit var mapView: TrajectoryMapView
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvError: TextView
    private lateinit var tvUncertainty: TextView
    private lateinit var tvTimeline: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button

    private var replayController: ReplayController? = null
    private var samples: List<NavigationSample> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadData()
        setupListeners()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.trajectoryMapView)
        statusCard = findViewById(R.id.cardStatus)
        statusTitle = findViewById(R.id.tvStatusTitle)
        statusSubtitle = findViewById(R.id.tvStatusSubtitle)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvPosition = findViewById(R.id.tvPosition)
        tvError = findViewById(R.id.tvError)
        tvUncertainty = findViewById(R.id.tvUncertainty)
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
            updateTelemetry(samples[0], 0, samples.size)
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
                updateTelemetry(samples[0], 0, samples.size)
            }
        }
    }

    override fun onSampleDispatched(sample: NavigationSample, sampleIndex: Int, totalSamples: Int) {
        runOnUiThread {
            updateTelemetry(sample, sampleIndex, totalSamples)
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

    private fun updateTelemetry(sample: NavigationSample, index: Int, total: Int) {
        if (sample.isGnssDenied) {
            statusCard.setCardBackgroundColor(Color.parseColor("#2A1215"))
            statusCard.strokeColor = Color.parseColor("#EF4444")
            statusTitle.text = "🔴 GNSS DENIED — DEAD RECKONING ACTIVE"
            statusTitle.setTextColor(Color.parseColor("#EF4444"))
            statusSubtitle.text = "Inertial & Wheel-Speed Propagation Active (GPS Masked)"
        } else {
            statusCard.setCardBackgroundColor(Color.parseColor("#0D2818"))
            statusCard.strokeColor = Color.parseColor("#10B981")
            statusTitle.text = "🟢 GNSS AVAILABLE"
            statusTitle.setTextColor(Color.parseColor("#10B981"))
            statusSubtitle.text = "GPS Position Fix & EKF Measurement Update Nominal"
        }

        tvSpeed.text = String.format("%.1f km/h", sample.speedKmh)
        tvPosition.text = String.format("X: %.1f m, Y: %.1f m", sample.estimatedX, sample.estimatedY)
        tvError.text = String.format("%.2f m", sample.errorMeters)
        tvUncertainty.text = String.format("±%.1f m", sample.uncertaintyMeters)

        val totalTime = if (samples.isNotEmpty()) samples.last().timestamp else 24.9
        tvTimeline.text = String.format("t = %.1f s / %.1f s  (Sample %d/%d)", sample.timestamp, totalTime, index + 1, total)
        progressBar.progress = index
    }

    override fun onDestroy() {
        super.onDestroy()
        replayController?.release()
    }
}
