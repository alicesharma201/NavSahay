package com.navsahay.app.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.navsahay.app.data.NavigationSample

enum class PlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    COMPLETED
}

interface ReplayListener {
    fun onSampleDispatched(sample: NavigationSample, sampleIndex: Int, totalSamples: Int)
    fun onStateChanged(state: PlaybackState)
}

class ReplayController(
    private val samples: List<NavigationSample>,
    private val listener: ReplayListener
) {
    private val handler = Handler(Looper.getMainLooper())
    private var state = PlaybackState.IDLE
    private var currentIndex = 0
    private var playStartTimeMs = 0L
    private var simulatedTimeOffsetMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (state != PlaybackState.PLAYING) return

            val now = SystemClock.elapsedRealtime()
            val elapsedSec = (now - playStartTimeMs + simulatedTimeOffsetMs) / 1000.0

            // Advance sample index based on exact timestamps in demo_route.json
            while (currentIndex < samples.size && samples[currentIndex].timestamp <= elapsedSec) {
                listener.onSampleDispatched(samples[currentIndex], currentIndex, samples.size)
                currentIndex++
            }

            if (currentIndex >= samples.size) {
                state = PlaybackState.COMPLETED
                listener.onStateChanged(state)
            } else {
                handler.postDelayed(this, 30L)
            }
        }
    }

    fun start() {
        if (samples.isEmpty()) return
        if (state == PlaybackState.COMPLETED || state == PlaybackState.IDLE) {
            currentIndex = 0
            simulatedTimeOffsetMs = 0L
        }

        state = PlaybackState.PLAYING
        playStartTimeMs = SystemClock.elapsedRealtime()
        listener.onStateChanged(state)

        if (currentIndex < samples.size) {
            listener.onSampleDispatched(samples[currentIndex], currentIndex, samples.size)
        }
        handler.post(tickRunnable)
    }

    fun pause() {
        if (state == PlaybackState.PLAYING) {
            handler.removeCallbacks(tickRunnable)
            val now = SystemClock.elapsedRealtime()
            simulatedTimeOffsetMs += (now - playStartTimeMs)
            state = PlaybackState.PAUSED
            listener.onStateChanged(state)
        }
    }

    fun reset() {
        handler.removeCallbacks(tickRunnable)
        currentIndex = 0
        simulatedTimeOffsetMs = 0L
        state = PlaybackState.IDLE
        listener.onStateChanged(state)
        if (samples.isNotEmpty()) {
            listener.onSampleDispatched(samples[0], 0, samples.size)
        }
    }

    fun release() {
        handler.removeCallbacks(tickRunnable)
    }
}
