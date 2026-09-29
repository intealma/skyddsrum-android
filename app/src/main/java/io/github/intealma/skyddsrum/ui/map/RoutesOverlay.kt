package io.github.intealma.skyddsrum.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.os.SystemClock
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.LatLon
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.pow

/**
 * Draws the routes to the 3 best shelters like the website: green lines with a soft glow that grow
 * from the user to each shelter (staggered), red numbered target dots and a white origin dot.
 */
class RoutesOverlay(private val density: Float, typeface: Typeface?) : Overlay() {

    private var origin: LatLon? = null
    private var targets: List<LatLon> = emptyList()
    private var lines: List<List<LatLon>> = emptyList()
    private var cumulative: List<DoubleArray> = emptyList()
    private var animStart = 0L
    private var version = -1

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFF2DD45A.toInt(); alpha = 90
        strokeWidth = 8f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFF3BFF6E.toInt(); alpha = 242
        strokeWidth = 2.4f * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val targetHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; alpha = 64 }
    private val targetFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F7F5.toInt() }
    private val whiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2f * density
    }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val originHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F7F5.toInt(); alpha = 90 }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = 11f * density; isFakeBoldText = true
        if (typeface != null) this.typeface = typeface
    }
    private val path = Path()
    private val pt = Point()
    private val geo = GeoPoint(0.0, 0.0)

    /** Updates what is drawn; new [linesVersion] restarts the grow-in animation. */
    fun set(origin: LatLon?, targets: List<LatLon>, lines: List<List<LatLon>>, linesVersion: Int) {
        this.origin = origin
        this.targets = targets
        if (linesVersion != version) {
            version = linesVersion
            this.lines = lines
            cumulative = lines.map { pts ->
                DoubleArray(pts.size).also { acc ->
                    for (i in 1 until pts.size) {
                        acc[i] = acc[i - 1] + Geo.haversineMeters(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
                    }
                }
            }
            animStart = SystemClock.uptimeMillis()
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val now = SystemClock.uptimeMillis()
        var animating = false

        lines.forEachIndexed { i, pts ->
            if (pts.size < 2) return@forEachIndexed
            val t = ((now - animStart - i * STAGGER_MS).toFloat() / DURATION_MS).coerceIn(0f, 1f)
            if (t < 1f) animating = true
            val eased = 1f - (1f - t).pow(3)
            buildPartialPath(projection, pts, cumulative[i], eased.toDouble())
            canvas.drawPath(path, glow)
            canvas.drawPath(path, line)
        }

        targets.forEachIndexed { i, target ->
            val (x, y) = toScreen(projection, target)
            canvas.drawCircle(x, y, 14f * density, targetHalo)
            canvas.drawCircle(x, y, 9f * density, targetFill)
            canvas.drawCircle(x, y, 9f * density, whiteRing)
            canvas.drawText("${i + 1}", x, y - (number.ascent() + number.descent()) / 2, number)
        }

        origin?.let {
            val (x, y) = toScreen(projection, it)
            canvas.drawCircle(x, y, 12f * density, originHalo)
            canvas.drawCircle(x, y, 8.5f * density, black)
            canvas.drawCircle(x, y, 5.5f * density, white)
        }

        if (animating) mapView.postInvalidateOnAnimation()
    }

    /** Path along [pts] covering [fraction] of the total length (for the grow-in animation). */
    private fun buildPartialPath(projection: Projection, pts: List<LatLon>, acc: DoubleArray, fraction: Double) {
        path.reset()
        val target = acc.last() * fraction
        var (x, y) = toScreen(projection, pts[0])
        path.moveTo(x, y)
        for (i in 1 until pts.size) {
            if (acc[i] >= target) {
                val seg = acc[i] - acc[i - 1]
                val f = if (seg == 0.0) 0.0 else (target - acc[i - 1]) / seg
                val a = pts[i - 1]
                val b = pts[i]
                val (px, py) = toScreen(projection, LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f))
                path.lineTo(px, py)
                return
            }
            toScreen(projection, pts[i]).let { (px, py) -> x = px; y = py }
            path.lineTo(x, y)
        }
    }

    private fun toScreen(projection: Projection, p: LatLon): Pair<Float, Float> {
        geo.setCoords(p.lat, p.lon)
        projection.toPixels(geo, pt)
        return pt.x.toFloat() to pt.y.toFloat()
    }

    private companion object {
        const val DURATION_MS = 650L
        const val STAGGER_MS = 100L
        const val RED = 0xFFFF3B3B.toInt()
    }
}
