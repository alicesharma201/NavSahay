package com.navsahay.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.navsahay.app.data.NavigationSample
import kotlin.math.cos
import kotlin.math.sin

class TrajectoryMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var allSamples: List<NavigationSample> = emptyList()
    private var currentSampleIndex: Int = -1
    private var currentSample: NavigationSample? = null

    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val refPath = Path()
    private val estPathAvailablePre = Path()
    private val estPathDenied = Path()
    private val estPathAvailablePost = Path()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 26f
        typeface = Typeface.MONOSPACE
    }

    private val refPathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        strokeWidth = 4.0f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val estAvailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        strokeWidth = 6.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val estDeniedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        strokeWidth = 7.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val uncertaintyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20EF4444")
        style = Paint.Style.FILL
    }

    private val uncertaintyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60EF4444")
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val vehicleCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7")
        style = Paint.Style.FILL
    }

    private val vehiclePulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#400284C7")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        strokeWidth = 4f
    }

    fun setRouteData(samples: List<NavigationSample>) {
        this.allSamples = samples
        calculateBounds()
        buildReferencePath()
        currentSampleIndex = -1
        currentSample = null
        rebuildEstimatedPaths()
        invalidate()
    }

    fun setCurrentSample(sample: NavigationSample, index: Int) {
        this.currentSample = sample
        this.currentSampleIndex = index
        rebuildEstimatedPaths()
        invalidate()
    }

    fun reset() {
        currentSampleIndex = -1
        currentSample = null
        rebuildEstimatedPaths()
        invalidate()
    }

    private fun calculateBounds() {
        if (allSamples.isEmpty()) return
        minX = allSamples.minOf { it.groundTruthX.toFloat() }
        maxX = allSamples.maxOf { it.groundTruthX.toFloat() }
        minY = allSamples.minOf { it.groundTruthY.toFloat() }
        maxY = allSamples.maxOf { it.groundTruthY.toFloat() }

        val spanX = maxOf(10f, maxX - minX)
        val spanY = maxOf(10f, maxY - minY)
        val marginX = spanX * 0.15f
        val marginY = spanY * 0.15f

        minX -= marginX
        maxX += marginX
        minY -= marginY
        maxY += marginY
    }

    private fun buildReferencePath() {
        refPath.reset()
        if (allSamples.isEmpty()) return
        for (i in allSamples.indices) {
            val s = allSamples[i]
            val px = toScreenX(s.groundTruthX.toFloat())
            val py = toScreenY(s.groundTruthY.toFloat())
            if (i == 0) refPath.moveTo(px, py) else refPath.lineTo(px, py)
        }
    }

    private fun rebuildEstimatedPaths() {
        estPathAvailablePre.reset()
        estPathDenied.reset()
        estPathAvailablePost.reset()

        if (allSamples.isEmpty() || currentSampleIndex < 0) return

        val limit = minOf(currentSampleIndex, allSamples.size - 1)
        for (i in 0..limit) {
            val s = allSamples[i]
            val px = toScreenX(s.estimatedX.toFloat())
            val py = toScreenY(s.estimatedY.toFloat())

            if (s.timestamp < 5.0) {
                if (estPathAvailablePre.isEmpty) estPathAvailablePre.moveTo(px, py) else estPathAvailablePre.lineTo(px, py)
            } else if (s.timestamp <= 20.0) {
                if (estPathDenied.isEmpty) {
                    val prev = allSamples[maxOf(0, i - 1)]
                    estPathDenied.moveTo(toScreenX(prev.estimatedX.toFloat()), toScreenY(prev.estimatedY.toFloat()))
                }
                estPathDenied.lineTo(px, py)
            } else {
                if (estPathAvailablePost.isEmpty) {
                    val prev = allSamples[maxOf(0, i - 1)]
                    estPathAvailablePost.moveTo(toScreenX(prev.estimatedX.toFloat()), toScreenY(prev.estimatedY.toFloat()))
                }
                estPathAvailablePost.lineTo(px, py)
            }
        }
    }

    private fun updateTransform() {
        val w = width.toFloat() - paddingLeft - paddingRight
        val h = height.toFloat() - paddingTop - paddingBottom
        if (w <= 0 || h <= 0 || allSamples.isEmpty()) return

        val rangeX = maxX - minX
        val rangeY = maxY - minY
        val scaleX = w / rangeX
        val scaleY = h / rangeY
        scale = minOf(scaleX, scaleY)

        val scaledW = rangeX * scale
        val scaledH = rangeY * scale
        offsetX = paddingLeft + (w - scaledW) / 2f
        offsetY = paddingTop + (h - scaledH) / 2f

        buildReferencePath()
        rebuildEstimatedPaths()
    }

    private fun toScreenX(xMeters: Float): Float = offsetX + (xMeters - minX) * scale
    private fun toScreenY(yMeters: Float): Float = offsetY + (maxY - yMeters) * scale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTransform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#FFFFFF"))

        if (allSamples.isEmpty()) return

        drawGrid(canvas)
        canvas.drawPath(refPath, refPathPaint)
        canvas.drawPath(estPathAvailablePre, estAvailPaint)
        canvas.drawPath(estPathDenied, estDeniedPaint)
        canvas.drawPath(estPathAvailablePost, estAvailPaint)
        drawMilestones(canvas)

        currentSample?.let { s ->
            val vx = toScreenX(s.estimatedX.toFloat())
            val vy = toScreenY(s.estimatedY.toFloat())

            val radiusPx = (s.uncertaintyMeters.toFloat() * scale).coerceAtLeast(8f)
            if (s.isGnssDenied) {
                uncertaintyPaint.color = Color.parseColor("#20EF4444")
                uncertaintyStrokePaint.color = Color.parseColor("#60EF4444")
            } else {
                uncertaintyPaint.color = Color.parseColor("#2010B981")
                uncertaintyStrokePaint.color = Color.parseColor("#6010B981")
            }
            canvas.drawCircle(vx, vy, radiusPx, uncertaintyPaint)
            canvas.drawCircle(vx, vy, radiusPx, uncertaintyStrokePaint)

            canvas.drawCircle(vx, vy, 16f, vehiclePulsePaint)
            canvas.drawCircle(vx, vy, 10f, vehicleCorePaint)

            if (currentSampleIndex > 0) {
                val prev = allSamples[currentSampleIndex - 1]
                val dx = s.estimatedX - prev.estimatedX
                val dy = s.estimatedY - prev.estimatedY
                val angle = Math.atan2(-dy, dx).toFloat()
                val headX = vx + cos(angle) * 22f
                val headY = vy + sin(angle) * 22f
                canvas.drawLine(vx, vy, headX, headY, arrowPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val stepMeters = 50f
        var x = (minX / stepMeters).toInt() * stepMeters
        while (x <= maxX) {
            val sx = toScreenX(x)
            canvas.drawLine(sx, 0f, sx, height.toFloat(), gridPaint)
            canvas.drawText("${x.toInt()}m", sx + 6f, height - 16f, textPaint)
            x += stepMeters
        }

        var y = (minY / stepMeters).toInt() * stepMeters
        while (y <= maxY) {
            val sy = toScreenY(y)
            canvas.drawLine(0f, sy, width.toFloat(), sy, gridPaint)
            canvas.drawText("${y.toInt()}m", 16f, sy - 6f, textPaint)
            y += stepMeters
        }
    }

    private fun drawMilestones(canvas: Canvas) {
        if (allSamples.size > 50) {
            val s5 = allSamples[50]
            val x5 = toScreenX(s5.groundTruthX.toFloat())
            val y5 = toScreenY(s5.groundTruthY.toFloat())
            markerPaint.color = Color.parseColor("#2563EB")
            canvas.drawCircle(x5, y5, 7f, markerPaint)
        }
        if (allSamples.size > 200) {
            val s20 = allSamples[200]
            val x20 = toScreenX(s20.groundTruthX.toFloat())
            val y20 = toScreenY(s20.groundTruthY.toFloat())
            markerPaint.color = Color.parseColor("#7C3AED")
            canvas.drawCircle(x20, y20, 7f, markerPaint)
        }
    }
}
