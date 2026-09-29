package io.github.intealma.skyddsrum.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import io.github.intealma.skyddsrum.data.Shelter
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.tan

/**
 * Draws all ~63 000 shelters with grid-based clustering. Markers would be far too slow at this
 * size, so clusters are precomputed per zoom level (in Web Mercator space) on a background
 * thread and drawn directly on the canvas. Tapping a cluster zooms in; tapping a single shelter
 * calls [onShelterTap].
 */
class ShelterClusterOverlay(
    private val shelters: List<Shelter>,
    private val density: Float,
    typeface: Typeface?,
    private val onShelterTap: (Shelter) -> Unit,
) : Overlay() {

    private class Cluster(val lat: Double, val lon: Double, val count: Int, val shelter: Shelter?)
    private class Drawn(val x: Float, val y: Float, val radius: Float, val cluster: Cluster)

    var selectedId: String? = null

    private val cache = ConcurrentHashMap<Int, List<Cluster>>()
    private val drawn = ArrayList<Drawn>()
    private val point = Point()
    private val geo = GeoPoint(0.0, 0.0)

    // Styling follows the website: dark clusters with a white ring, white outline shelter marks, red selection.
    private val clusterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60A0B0F.toInt() }
    private val clusterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = LINE_WHITE; strokeWidth = 1.4f * density
    }
    private val markStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = LINE_WHITE; strokeWidth = 1.6f * density; strokeJoin = Paint.Join.ROUND
    }
    private val markFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val selectedHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; alpha = 64 }
    private val selectedDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
    private val selectedRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LINE_WHITE; textAlign = Paint.Align.CENTER; textSize = 11f * density
        if (typeface != null) this.typeface = typeface
    }
    private val triangle = Path()

    init {
        // Warm the cache for the zoom levels people use most, off the UI thread.
        Executors.newSingleThreadExecutor().apply {
            execute { (MIN_CLUSTER_ZOOM..SINGLE_ZOOM).forEach { clustersFor(it) } }
            shutdown()
        }
    }

    private fun clustersFor(zoom: Int): List<Cluster> {
        val z = zoom.coerceIn(MIN_CLUSTER_ZOOM, SINGLE_ZOOM)
        return cache.getOrPut(z) { buildClusters(z) }
    }

    private fun buildClusters(zoom: Int): List<Cluster> {
        if (zoom >= SINGLE_ZOOM) return shelters.map { Cluster(it.lat, it.lon, 1, it) }
        // Cell size in normalized Mercator units: CELL_DP screen dp at this zoom (tiles are dp-scaled).
        val cells = (256.0 * (1 shl zoom)) / CELL_DP
        class Acc(var lat: Double, var lon: Double, var n: Int, val first: Shelter)
        val grid = HashMap<Long, Acc>()
        for (s in shelters) {
            val cx = floor(mercX(s.lon) * cells).toLong()
            val cy = floor(mercY(s.lat) * cells).toLong()
            val key = (cx shl 32) or (cy and 0xffffffffL)
            val acc = grid[key]
            if (acc == null) grid[key] = Acc(s.lat, s.lon, 1, s)
            else { acc.lat += s.lat; acc.lon += s.lon; acc.n++ }
        }
        return grid.values.map { a ->
            if (a.n == 1) Cluster(a.lat, a.lon, 1, a.first) else Cluster(a.lat / a.n, a.lon / a.n, a.n, null)
        }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        drawn.clear()
        val box = projection.boundingBox
        val latPad = (box.latNorth - box.latSouth) * 0.1
        val lonPad = (box.lonEast - box.lonWest) * 0.1
        val south = box.latSouth - latPad
        val north = box.latNorth + latPad
        val west = box.lonWest - lonPad
        val east = box.lonEast + lonPad

        var selected: Drawn? = null
        for (c in clustersFor(floor(projection.zoomLevel).toInt())) {
            if (c.lat < south || c.lat > north || c.lon < west || c.lon > east) continue
            geo.setCoords(c.lat, c.lon)
            projection.toPixels(geo, point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (c.shelter != null) {
                val half = 7f * density
                drawMark(canvas, x, y, half)
                val d = Drawn(x, y, half, c)
                drawn += d
                if (c.shelter.id == selectedId) selected = d
            } else {
                val r = clusterRadius(c.count) * density
                canvas.drawCircle(x, y, r, clusterFill)
                canvas.drawCircle(x, y, r, clusterRing)
                canvas.drawText(label(c.count), x, y - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
                drawn += Drawn(x, y, r, c)
            }
        }
        selected?.let {
            canvas.drawCircle(it.x, it.y, 14f * density, selectedHalo)
            canvas.drawCircle(it.x, it.y, 6f * density, selectedDot)
            canvas.drawCircle(it.x, it.y, 6f * density, selectedRing)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val slop = 10f * density
        val hit = drawn
            .map { it to hypot(it.x - e.x, it.y - e.y) }
            .filter { (d, dist) -> dist <= d.radius + slop }
            .minByOrNull { it.second }?.first ?: return false
        val c = hit.cluster
        if (c.shelter != null) {
            onShelterTap(c.shelter)
        } else {
            val target = (floor(mapView.zoomLevelDouble) + 2).coerceAtMost(SINGLE_ZOOM.toDouble())
            mapView.controller.animateTo(GeoPoint(c.lat, c.lon), target, 400L)
        }
        return true
    }

    /** Outline square with a triangle inside, like the website's map icon. */
    private fun drawMark(canvas: Canvas, x: Float, y: Float, half: Float) {
        canvas.drawRect(x - half, y - half, x + half, y + half, markFill)
        canvas.drawRect(x - half, y - half, x + half, y + half, markStroke)
        triangle.reset()
        triangle.moveTo(x, y - half * 0.55f)
        triangle.lineTo(x + half * 0.6f, y + half * 0.45f)
        triangle.lineTo(x - half * 0.6f, y + half * 0.45f)
        triangle.close()
        canvas.drawPath(triangle, markStroke)
    }

    /** Radius in dp by count, same steps as the website (1→9, 20→12, 100→17, 1000→24, 8000→32). */
    private fun clusterRadius(n: Int): Float {
        val stops = floatArrayOf(1f, 20f, 100f, 1000f, 8000f)
        val radii = floatArrayOf(9f, 12f, 17f, 24f, 32f)
        val v = n.toFloat()
        if (v >= stops.last()) return radii.last()
        val i = stops.indexOfLast { it <= v }.coerceAtLeast(0)
        val t = (v - stops[i]) / (stops[i + 1] - stops[i])
        return radii[i] + t * (radii[i + 1] - radii[i])
    }

    private fun label(n: Int) = if (n >= 1000) "${n / 1000}k" else n.toString()

    companion object {
        /** From this zoom level every shelter is drawn individually. */
        const val SINGLE_ZOOM = 15
        private const val MIN_CLUSTER_ZOOM = 3
        private const val CELL_DP = 56.0
        private const val LINE_WHITE = 0xFFF7F7F5.toInt()
        private const val RED = 0xFFFF3B3B.toInt()

        private fun mercX(lon: Double) = (lon + 180.0) / 360.0
        private fun mercY(lat: Double): Double {
            val r = Math.toRadians(lat)
            return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0
        }
    }
}
