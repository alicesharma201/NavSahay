package com.navsahay.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.navsahay.app.R
import com.navsahay.app.data.GhatKiGuniRoute
import com.navsahay.app.data.NavigationSample
import com.navsahay.app.data.RoutePosition
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class TrajectoryMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TrajectoryMapView"
    }

    private var mapBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // Current State
    private var currentSample: NavigationSample? = null
    private var currentRouteProgress: Float = 0f
    private var currentVehiclePos: RoutePosition = GhatKiGuniRoute.getPositionAtProgress(0f)

    // Transformation Parameters (Guarantees exact geometric lock between Bitmap and Route)
    private var dstRect = RectF()
    private var scale = 1f
    private var dstW = 0f
    private var dstH = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // Debug mode (displays all 53 points and progress info)
    var isDebugMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Static Paths
    private val approachPath = Path()
    private val tunnelPath = Path()
    private val exitPath = Path()
    private val activeTrailPath = Path()

    // Paints
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0F172A")
        style = Paint.Style.FILL
    }

    // Route Paints
    private val approachGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8006B6D4") // Cyan glow
        strokeWidth = 9.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val approachCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Vibrant Cyan/Sky
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tunnelCasingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D00F172A") // Dark protective tunnel casing
        strokeWidth = 18.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tunnelWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        strokeWidth = 1.2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    private val tunnelGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80EF4444") // Red glow
        strokeWidth = 9.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tunnelCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F87171") // Vivid Crimson Red
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val activeTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Uncertainty Paints
    private val uncertaintyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val uncertaintyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }

    // Vehicle Marker Paints
    private val vehicleAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#300284C7")
        style = Paint.Style.FILL
    }

    private val vehicleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val vehicleCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7")
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    // Landmark Badge Paints
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EB0F172A")
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }

    private val badgeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val badgeSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 17f
    }

    private val leaderLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0FFFFFF")
        strokeWidth = 1.2f
    }

    // Debug Paint
    private val debugPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        textSize = 20f
        typeface = Typeface.MONOSPACE
    }

    init {
        loadBitmap()
        setOnClickListener {
            isDebugMode = !isDebugMode
        }
    }

    private fun loadBitmap() {
        try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            mapBitmap = BitmapFactory.decodeResource(resources, R.drawable.map_ghat_ki_guni, options)
            Log.i(TAG, "Successfully loaded map_ghat_ki_guni: ${mapBitmap?.width}x${mapBitmap?.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load map_ghat_ki_guni bitmap", e)
        }
    }

    fun setRouteData(samples: List<NavigationSample>) {
        currentRouteProgress = 0f
        currentVehiclePos = GhatKiGuniRoute.getPositionAtProgress(0f)
        currentSample = if (samples.isNotEmpty()) samples[0] else null
        rebuildStaticPaths()
        invalidate()
    }

    /**
     * Updates the vehicle position based on replay timestamp and sample telemetry.
     */
    fun setCurrentSample(sample: NavigationSample, index: Int, totalSamples: Int = 950) {
        this.currentSample = sample
        this.currentRouteProgress = GhatKiGuniRoute.getProgressForTime(sample.timestamp, GhatKiGuniRoute.TOTAL_DURATION_SEC)
        this.currentVehiclePos = GhatKiGuniRoute.getPositionAtProgress(currentRouteProgress)

        rebuildActiveTrail()
        invalidate()
    }

    fun reset() {
        currentRouteProgress = 0f
        currentVehiclePos = GhatKiGuniRoute.getPositionAtProgress(0f)
        rebuildActiveTrail()
        invalidate()
    }

    // --- EXACT COORDINATE TRANSFORMATION ---

    fun toScreenX(normX: Float): Float = offsetX + normX * dstW
    fun toScreenY(normY: Float): Float = offsetY + normY * dstH

    private fun updateTransform() {
        val w = width.toFloat() - paddingLeft - paddingRight
        val h = height.toFloat() - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return

        val scaleX = w / GhatKiGuniRoute.BITMAP_WIDTH
        val scaleY = h / GhatKiGuniRoute.BITMAP_HEIGHT
        scale = minOf(scaleX, scaleY)

        dstW = GhatKiGuniRoute.BITMAP_WIDTH * scale
        dstH = GhatKiGuniRoute.BITMAP_HEIGHT * scale
        offsetX = paddingLeft + (w - dstW) / 2f
        offsetY = paddingTop + (h - dstH) / 2f

        dstRect.set(offsetX, offsetY, offsetX + dstW, offsetY + dstH)

        rebuildStaticPaths()
        rebuildActiveTrail()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (w > 0) {
            val h = (w * GhatKiGuniRoute.BITMAP_HEIGHT / GhatKiGuniRoute.BITMAP_WIDTH).toInt()
            setMeasuredDimension(w, h)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTransform()
    }

    private fun rebuildStaticPaths() {
        approachPath.reset()
        tunnelPath.reset()
        exitPath.reset()

        val pts = GhatKiGuniRoute.points
        if (pts.size < 53) return

        // 1. Approach (0 to 25)
        for (i in 0..GhatKiGuniRoute.INDEX_WEST_PORTAL) {
            val sx = toScreenX(pts[i].x)
            val sy = toScreenY(pts[i].y)
            if (i == 0) approachPath.moveTo(sx, sy) else approachPath.lineTo(sx, sy)
        }

        // 2. Tunnel (25 to 36)
        for (i in GhatKiGuniRoute.INDEX_WEST_PORTAL..GhatKiGuniRoute.INDEX_EAST_PORTAL) {
            val sx = toScreenX(pts[i].x)
            val sy = toScreenY(pts[i].y)
            if (i == GhatKiGuniRoute.INDEX_WEST_PORTAL) tunnelPath.moveTo(sx, sy) else tunnelPath.lineTo(sx, sy)
        }

        // 3. Exit (36 to 52)
        for (i in GhatKiGuniRoute.INDEX_EAST_PORTAL until pts.size) {
            val sx = toScreenX(pts[i].x)
            val sy = toScreenY(pts[i].y)
            if (i == GhatKiGuniRoute.INDEX_EAST_PORTAL) exitPath.moveTo(sx, sy) else exitPath.lineTo(sx, sy)
        }
    }

    private fun rebuildActiveTrail() {
        activeTrailPath.reset()
        val pts = GhatKiGuniRoute.points
        if (pts.isEmpty()) return

        val segIndex = currentVehiclePos.segmentIndex
        val currentVx = toScreenX(currentVehiclePos.normalizedX)
        val currentVy = toScreenY(currentVehiclePos.normalizedY)

        for (i in 0..segIndex) {
            val sx = toScreenX(pts[i].x)
            val sy = toScreenY(pts[i].y)
            if (i == 0) activeTrailPath.moveTo(sx, sy) else activeTrailPath.lineTo(sx, sy)
        }
        if (segIndex < pts.size) {
            activeTrailPath.lineTo(currentVx, currentVy)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 1. Draw Satellite Map Bitmap (Locked to dstRect)
        mapBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, dstRect, bitmapPaint)
        }

        // 2. Draw Tunnel Casing (Underground corridor)
        canvas.drawPath(tunnelPath, tunnelCasingPaint)

        // Draw Tunnel Wall Boundaries
        val pWest = GhatKiGuniRoute.points[GhatKiGuniRoute.INDEX_WEST_PORTAL]
        val pEast = GhatKiGuniRoute.points[GhatKiGuniRoute.INDEX_EAST_PORTAL]
        val wx = toScreenX(pWest.x)
        val wy = toScreenY(pWest.y)
        val ex = toScreenX(pEast.x)
        val ey = toScreenY(pEast.y)
        val tdx = ex - wx
        val tdy = ey - wy
        val tlen = hypot(tdx, tdy).coerceAtLeast(1f)
        val tnx = -tdy / tlen * (9f * scale)
        val tny = tdx / tlen * (9f * scale)
        canvas.drawLine(wx + tnx, wy + tny, ex + tnx, ey + tny, tunnelWallPaint)
        canvas.drawLine(wx - tnx, wy - tny, ex - tnx, ey - tny, tunnelWallPaint)

        // 3. Draw Static Full Route Base (Cyan Approach + Red Tunnel + Cyan Exit)
        canvas.drawPath(approachPath, approachGlowPaint)
        canvas.drawPath(approachPath, approachCorePaint)

        canvas.drawPath(tunnelPath, tunnelGlowPaint)
        canvas.drawPath(tunnelPath, tunnelCorePaint)

        canvas.drawPath(exitPath, approachGlowPaint)
        canvas.drawPath(exitPath, approachCorePaint)

        // 4. Draw Active Trajectory Trail (Bright white center core)
        canvas.drawPath(activeTrailPath, activeTrailPaint)

        // 5. Draw Landmark Badges
        drawLandmarkBadges(canvas)

        // 6. Draw Vehicle & Dynamic Uncertainty Halo
        drawVehicle(canvas)

        // 7. Debug Mode Overlay (if enabled)
        if (isDebugMode) {
            drawDebugOverlay(canvas)
        }
    }

    private fun drawVehicle(canvas: Canvas) {
        val vx = toScreenX(currentVehiclePos.normalizedX)
        val vy = toScreenY(currentVehiclePos.normalizedY)
        val sample = currentSample
        val t = sample?.timestamp ?: 0.0

        // Configure Uncertainty Halo color matching active state
        when {
            t < GhatKiGuniRoute.TIME_ENTRY_SEC -> {
                uncertaintyFillPaint.color = Color.parseColor("#1810B981") // Green
                uncertaintyStrokePaint.color = Color.parseColor("#6010B981")
            }
            t < GhatKiGuniRoute.TIME_CELLULAR_SEC -> {
                uncertaintyFillPaint.color = Color.parseColor("#22EF4444") // Red
                uncertaintyStrokePaint.color = Color.parseColor("#70EF4444")
            }
            t < GhatKiGuniRoute.TIME_EXIT_SEC -> {
                uncertaintyFillPaint.color = Color.parseColor("#22F59E0B") // Amber
                uncertaintyStrokePaint.color = Color.parseColor("#70F59E0B")
            }
            t < GhatKiGuniRoute.TIME_RECOVERY_END_SEC -> {
                uncertaintyFillPaint.color = Color.parseColor("#183B82F6") // Blue
                uncertaintyStrokePaint.color = Color.parseColor("#603B82F6")
            }
            else -> {
                uncertaintyFillPaint.color = Color.parseColor("#1810B981") // Green
                uncertaintyStrokePaint.color = Color.parseColor("#6010B981")
            }
        }

        // Draw Uncertainty Halo
        val uncMeters = sample?.uncertaintyMeters ?: 2.0
        val radiusPx = (uncMeters.toFloat() * 3.5f * scale).coerceIn(10f * scale, 32f * scale)
        canvas.drawCircle(vx, vy, radiusPx, uncertaintyFillPaint)
        canvas.drawCircle(vx, vy, radiusPx, uncertaintyStrokePaint)

        // Draw Vehicle Puck
        val puckRadius = 6.5f * scale.coerceAtLeast(0.8f)
        canvas.drawCircle(vx, vy, puckRadius * 1.6f, vehicleAuraPaint)
        canvas.drawCircle(vx, vy, puckRadius, vehicleCorePaint)
        canvas.drawCircle(vx, vy, puckRadius, vehicleBorderPaint)

        // Draw Directional Heading Arrow
        val heading = currentVehiclePos.headingRad
        val arrowLen = 14f * scale.coerceAtLeast(0.8f)
        val hx = vx + cos(heading) * arrowLen
        val hy = vy + sin(heading) * arrowLen
        canvas.drawLine(vx, vy, hx, hy, arrowPaint)
    }

    private fun drawLandmarkBadges(canvas: Canvas) {
        val pts = GhatKiGuniRoute.points
        if (pts.size < 53) return

        // 1. START
        val pStart = pts[GhatKiGuniRoute.INDEX_START]
        drawBadge(
            canvas = canvas,
            normX = pStart.x,
            normY = pStart.y,
            title = "START",
            subtitle = "Jaipur Approach",
            dotColor = Color.parseColor("#10B981"),
            offsetX = 14f * scale,
            offsetY = 10f * scale
        )

        // 2. GNSS LOST / WEST PORTAL
        val pWest = pts[GhatKiGuniRoute.INDEX_WEST_PORTAL]
        drawBadge(
            canvas = canvas,
            normX = pWest.x,
            normY = pWest.y,
            title = "GNSS LOST",
            subtitle = "West Portal • 14s",
            dotColor = Color.parseColor("#EF4444"),
            offsetX = -135f * scale,
            offsetY = -30f * scale
        )

        // 3. GNSS RECOVERED / EAST PORTAL
        val pEast = pts[GhatKiGuniRoute.INDEX_EAST_PORTAL]
        drawBadge(
            canvas = canvas,
            normX = pEast.x,
            normY = pEast.y,
            title = "GNSS RECOVERED",
            subtitle = "East Portal • 86s",
            dotColor = Color.parseColor("#3B82F6"),
            offsetX = 16f * scale,
            offsetY = 14f * scale
        )

        // 4. DESTINATION
        val pEnd = pts[GhatKiGuniRoute.INDEX_DESTINATION]
        drawBadge(
            canvas = canvas,
            normX = pEnd.x,
            normY = pEnd.y,
            title = "DESTINATION",
            subtitle = "Agra Road • 95s",
            dotColor = Color.parseColor("#F59E0B"),
            offsetX = -130f * scale,
            offsetY = -28f * scale
        )
    }

    private fun drawBadge(
        canvas: Canvas,
        normX: Float,
        normY: Float,
        title: String,
        subtitle: String?,
        dotColor: Int,
        offsetX: Float,
        offsetY: Float
    ) {
        val px = toScreenX(normX)
        val py = toScreenY(normY)

        val pinR = 4f * scale.coerceAtLeast(0.8f)
        badgeDotPaint.color = Color.WHITE
        canvas.drawCircle(px, py, pinR + 1.5f, badgeDotPaint)
        badgeDotPaint.color = dotColor
        canvas.drawCircle(px, py, pinR, badgeDotPaint)

        val titleSize = 13f * scale.coerceAtLeast(0.7f)
        val subSize = 10.5f * scale.coerceAtLeast(0.7f)
        badgeTitlePaint.textSize = titleSize
        badgeSubPaint.textSize = subSize

        val tw1 = badgeTitlePaint.measureText(title)
        val tw2 = subtitle?.let { badgeSubPaint.measureText(it) } ?: 0f
        val boxW = maxOf(tw1, tw2) + (18f * scale)
        val boxH = if (subtitle != null) 26f * scale else 17f * scale

        val bx = px + offsetX
        val by = py + offsetY

        val lx = if (offsetX > 0) bx else bx + boxW
        val ly = by + boxH / 2f
        canvas.drawLine(px, py, lx, ly, leaderLinePaint)

        val boxRect = RectF(bx, by, bx + boxW, by + boxH)
        canvas.drawRoundRect(boxRect, 4f * scale, 4f * scale, badgeBgPaint)

        badgeStrokePaint.color = dotColor
        badgeStrokePaint.alpha = 200
        canvas.drawRoundRect(boxRect, 4f * scale, 4f * scale, badgeStrokePaint)

        val dotR = 2.5f * scale
        val dotX = bx + 6f * scale
        val dotY = by + (if (subtitle != null) 7f * scale else 8.5f * scale)
        badgeDotPaint.color = dotColor
        canvas.drawCircle(dotX, dotY, dotR, badgeDotPaint)

        val textX = bx + 12f * scale
        val titleY = by + titleSize + 1f * scale
        canvas.drawText(title, textX, titleY, badgeTitlePaint)

        if (subtitle != null) {
            val subY = by + boxH - 4f * scale
            canvas.drawText(subtitle, textX, subY, badgeSubPaint)
        }
    }

    private fun drawDebugOverlay(canvas: Canvas) {
        val pts = GhatKiGuniRoute.points
        for (pt in pts) {
            val sx = toScreenX(pt.x)
            val sy = toScreenY(pt.y)
            canvas.drawCircle(sx, sy, 3f * scale, debugPointPaint)
        }

        val progressText = String.format("DEBUG: Progress=%.1f%% | Pt=%d/53", currentRouteProgress * 100f, currentVehiclePos.segmentIndex)
        canvas.drawText(progressText, offsetX + 10f, offsetY + dstH - 12f, debugTextPaint)
    }
}
