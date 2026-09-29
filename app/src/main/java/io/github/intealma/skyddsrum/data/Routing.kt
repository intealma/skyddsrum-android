package io.github.intealma.skyddsrum.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Travel modes, same as the website. Walk/bike/car use the public OSRM mirrors, transit uses Trafiklab ResRobot. */
enum class TravelMode(val osrmBase: String?, val osrmProfile: String?) {
    WALK("https://routing.openstreetmap.de/routed-foot", "foot"),
    BIKE("https://routing.openstreetmap.de/routed-bike", "bike"),
    CAR("https://routing.openstreetmap.de/routed-car", "driving"),
    TRANSIT(null, null),
}

data class LatLon(val lat: Double, val lon: Double)

/** Why a route is only approximate. */
enum class RouteNote { DIRECT_LINE, TRANSIT_NO_KEY, TRANSIT_FAILED, WALK_NO_TRANSIT }

/** One step of a public-transit trip (from ResRobot legs). */
sealed interface TransitStep {
    val dep: String
    val arr: String
    data class Walk(val to: String, override val dep: String, override val arr: String) : TransitStep
    data class Transfer(val at: String, override val dep: String, override val arr: String) : TransitStep
    data class Ride(
        val line: String,
        val towards: String?,
        val from: String,
        val to: String,
        override val dep: String,
        override val arr: String,
    ) : TransitStep
}

data class Route(
    val points: List<LatLon>,
    val distanceKm: Double,
    val durationMin: Double?,
    val approx: Boolean,
    val note: RouteNote? = null,
    val steps: List<TransitStep>? = null,
) {
    /** Ranking key when picking the best 3: real trip time, else ~15 min per km (same as the website). */
    val rankMinutes: Double get() = durationMin ?: (distanceKm * 15)
}

data class RoutedShelter(val shelter: Shelter, val route: Route)

/** The chosen 3 plus how many faster shelters were skipped because they are already full. */
data class Best3(val items: List<RoutedShelter>, val skippedFull: Int)

/**
 * Port of the website's "3 nearest by real travel time" logic:
 * - walk/bike/car: one OSRM Table (matrix) call ranks the 30 closest shelters (straight line) by real
 *   travel time; the top 3 are shown right away, then full route geometry is fetched for them.
 * - transit: ResRobot has no matrix endpoint, so the 8 closest are routed individually (max 4 at once).
 * Results are cached per mode + origin (~11 m precision). Failures fall back to a direct line.
 */
class Router(private val userAgent: String, private val trafiklabKey: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val routeCache = ConcurrentHashMap<String, Deferred<Route>>()
    private val matrixCache = ConcurrentHashMap<String, Deferred<List<MatrixCell>?>>()

    private data class MatrixCell(val durationMin: Double?, val distanceKm: Double?)

    /**
     * Finds the best 3 shelters for [mode]. [onInterim] gets the ranking with matrix numbers
     * before route lines are ready (points empty); the return value has full geometry.
     */
    suspend fun best3(
        mode: TravelMode,
        origin: LatLon,
        shelters: List<Shelter>,
        isFull: (Shelter) -> Boolean = { false },
        onInterim: (Best3) -> Unit = {},
    ): Best3 = coroutineScope {
        val key = originKey(origin)
        if (mode == TravelMode.TRANSIT) {
            val pool = Geo.nearest(shelters, origin.lat, origin.lon, TRANSIT_CANDIDATES).map { it.shelter }
            val limit = Semaphore(4)
            val routes = pool.map { s -> async { limit.withPermit { cachedRoute(mode, key, origin, s).await() } } }.awaitAll()
            return@coroutineScope pickAvailable(pool.zip(routes) { s, r -> RoutedShelter(s, r) }.sortedBy { it.route.rankMinutes }, isFull)
        }

        val wide = Geo.nearest(shelters, origin.lat, origin.lon, MATRIX_CANDIDATES).map { it.shelter }
        val matrixKey = "$mode|$key"
        val matrix = matrixCache.getOrPut(matrixKey) {
            scope.async { fetchTravelMatrix(mode, origin, wide.map { LatLon(it.lat, it.lon) }) }
        }.await()
        if (matrix == null) matrixCache.remove(matrixKey) // retry next time (e.g. back online)
        val ranked = wide.mapIndexed { i, s ->
            val straightKm = Geo.haversineMeters(origin.lat, origin.lon, s.lat, s.lon) / 1000
            val cell = matrix?.getOrNull(i)
            RoutedShelter(
                s,
                Route(emptyList(), cell?.distanceKm ?: straightKm, cell?.durationMin ?: (straightKm * 15), approx = false),
            )
        }.sortedBy { it.route.rankMinutes }
        val chosen = pickAvailable(ranked, isFull)
        onInterim(chosen)

        val routed = chosen.items.map { async { RoutedShelter(it.shelter, cachedRoute(mode, key, origin, it.shelter).await()) } }.awaitAll()
        chosen.copy(items = routed)
    }

    /** Like the website: skip shelters already at capacity, unless that would leave fewer than 3. */
    private fun pickAvailable(ranked: List<RoutedShelter>, isFull: (Shelter) -> Boolean): Best3 {
        val available = ranked.filterNot { isFull(it.shelter) }
        return if (available.size >= 3) Best3(available.take(3), ranked.size - available.size) else Best3(ranked.take(3), 0)
    }

    /** Warms the caches for the other road modes after the first result, like the website does. */
    fun prefetch(except: TravelMode, origin: LatLon, shelters: List<Shelter>, isFull: (Shelter) -> Boolean = { false }) {
        // One mode at a time with a pause: the free public OSRM mirrors answer bursts with HTTP 429.
        scope.launch {
            listOf(TravelMode.WALK, TravelMode.BIKE, TravelMode.CAR).filter { it != except }.forEach { m ->
                delay(PREFETCH_GAP_MS)
                runCatching { best3(m, origin, shelters, isFull) }
            }
        }
    }

    private fun cachedRoute(mode: TravelMode, originKey: String, origin: LatLon, s: Shelter): Deferred<Route> {
        val key = "$mode|$originKey|${s.id}"
        return routeCache.getOrPut(key) {
            scope.async {
                fetchRoute(mode, origin, LatLon(s.lat, s.lon)).also { if (it.approx) routeCache.remove(key) } // retry failures later
            }
        }
    }

    private fun fetchRoute(mode: TravelMode, start: LatLon, end: LatLon): Route {
        if (mode == TravelMode.TRANSIT) return fetchTransitRoute(start, end)
        return try {
            val url = "${mode.osrmBase}/route/v1/${mode.osrmProfile}/${start.lon},${start.lat};${end.lon},${end.lat}" +
                "?overview=full&geometries=geojson"
            val route = JSONObject(get(url)).getJSONArray("routes").getJSONObject(0)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coords.length()).map { i -> coords.getJSONArray(i).let { LatLon(it.getDouble(1), it.getDouble(0)) } }
            Route(points, route.getDouble("distance") / 1000, route.getDouble("duration") / 60, approx = false)
        } catch (e: Exception) {
            Log.w(TAG, "Routing failed for $mode, using a direct line", e)
            directLine(start, end, RouteNote.DIRECT_LINE)
        }
    }

    private fun fetchTravelMatrix(mode: TravelMode, start: LatLon, candidates: List<LatLon>): List<MatrixCell>? {
        if (candidates.isEmpty()) return null
        return try {
            val coords = (listOf(start) + candidates).joinToString(";") { "${it.lon},${it.lat}" }
            val destinations = candidates.indices.joinToString(";") { "${it + 1}" }
            val url = "${mode.osrmBase}/table/v1/${mode.osrmProfile}/$coords" +
                "?sources=0&destinations=$destinations&annotations=duration,distance"
            val data = JSONObject(get(url))
            val durations = data.getJSONArray("durations").getJSONArray(0)
            val distances = data.optJSONArray("distances")?.optJSONArray(0)
            candidates.indices.map { i ->
                MatrixCell(
                    durationMin = if (durations.isNull(i)) null else durations.getDouble(i) / 60,
                    distanceKm = if (distances == null || distances.isNull(i)) null else distances.getDouble(i) / 1000,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Matrix failed for $mode, ranking by straight line", e)
            null
        }
    }

    private fun fetchTransitRoute(start: LatLon, end: LatLon): Route {
        if (trafiklabKey.isBlank()) return directLine(start, end, RouteNote.TRANSIT_NO_KEY)
        return try {
            val url = "https://api.resrobot.se/v2.1/trip?format=json" +
                "&accessId=${URLEncoder.encode(trafiklabKey, "UTF-8")}" +
                "&originCoordLat=${start.lat}&originCoordLong=${start.lon}" +
                "&destCoordLat=${end.lat}&destCoordLong=${end.lon}" +
                "&passlist=1&numF=1&lang=${if (Locale.getDefault().language == "sv") "sv" else "en"}"
            parseTrip(get(url))
        } catch (e: HttpException) {
            if (e.body.contains("SVC_LOC")) {
                // ResRobot finds no trip when the shelter is closer than any stop: walking is the answer.
                fetchRoute(TravelMode.WALK, start, end).let { if (it.approx) it else it.copy(note = RouteNote.WALK_NO_TRANSIT) }
            } else {
                Log.w(TAG, "Transit routing failed (HTTP ${e.code}), using a direct line")
                directLine(start, end, RouteNote.TRANSIT_FAILED)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Transit routing failed, using a direct line", e)
            directLine(start, end, RouteNote.TRANSIT_FAILED)
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", userAgent)
        try {
            if (conn.responseCode != 200) {
                throw HttpException(conn.responseCode, conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private class HttpException(val code: Int, val body: String) : Exception("HTTP $code")

    companion object {
        private const val TAG = "Router"
        private const val MATRIX_CANDIDATES = 30
        private const val TRANSIT_CANDIDATES = 8
        private const val PREFETCH_GAP_MS = 600L

        /** ~11 m precision: GPS jitter reuses the cache, a real move gets fresh routes. */
        fun originKey(o: LatLon) = String.format(Locale.ROOT, "%.4f,%.4f", o.lon, o.lat)

        fun directLine(start: LatLon, end: LatLon, note: RouteNote) = Route(
            listOf(start, end),
            Geo.haversineMeters(start.lat, start.lon, end.lat, end.lon) / 1000,
            durationMin = null,
            approx = true,
            note = note,
        )

        fun parseIsoDurationMin(iso: String?): Double? {
            val m = Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""").find(iso ?: "") ?: return null
            val (h, min, s) = m.destructured
            return (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0) + (s.toIntOrNull() ?: 0) / 60.0
        }

        /** Parses a ResRobot v2.1 trip response: stop-to-stop line, total distance, duration and steps. */
        fun parseTrip(body: String): Route {
            val trip = JSONObject(body).getJSONArray("Trip").getJSONObject(0)
            val legs = trip.getJSONObject("LegList").arrayOrSingle("Leg")
            require(legs.isNotEmpty()) { "no legs" }
            val points = ArrayList<LatLon>()
            var km = 0.0
            for (leg in legs) {
                val stops = leg.optJSONObject("Stops")?.arrayOrSingle("Stop")?.map { it.latLon() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(leg.getJSONObject("Origin").latLon(), leg.getJSONObject("Destination").latLon())
                for (i in 1 until stops.size) km += Geo.haversineMeters(stops[i - 1].lat, stops[i - 1].lon, stops[i].lat, stops[i].lon) / 1000
                points += stops
            }
            return Route(points, km, parseIsoDurationMin(trip.optString("duration")), approx = false, steps = legs.map(::toStep))
        }

        private fun toStep(leg: JSONObject): TransitStep {
            val origin = leg.optJSONObject("Origin") ?: JSONObject()
            val dest = leg.optJSONObject("Destination") ?: JSONObject()
            val dep = origin.optString("time").take(5)
            val arr = dest.optString("time").take(5)
            return when (leg.optString("type")) {
                "WALK" -> TransitStep.Walk(dest.optString("name"), dep, arr)
                "TRSF" -> TransitStep.Transfer(origin.optString("name"), dep, arr)
                else -> {
                    val product = leg.arrayOrSingle("Product").firstOrNull()
                    val name = product?.let { p -> listOf("catOutS", "catOutL", "name").map { p.optString(it) }.firstOrNull { it.isNotBlank() } } ?: ""
                    val num = product?.let { p -> p.optString("line").ifBlank { p.optString("num") } } ?: ""
                    TransitStep.Ride(
                        line = listOf(name, num).filter { it.isNotBlank() }.joinToString(" "),
                        towards = leg.optString("direction").takeIf { it.isNotBlank() },
                        from = origin.optString("name"),
                        to = dest.optString("name"),
                        dep = dep,
                        arr = arr,
                    )
                }
            }
        }

        private fun JSONObject.latLon() = LatLon(getDouble("lat"), getDouble("lon")) // numbers or numeric strings

        /** ResRobot sometimes returns a single object where a list is expected. */
        private fun JSONObject.arrayOrSingle(name: String): List<JSONObject> {
            optJSONArray(name)?.let { arr -> return (0 until arr.length()).map { arr.getJSONObject(it) } }
            return optJSONObject(name)?.let { listOf(it) } ?: emptyList()
        }
    }
}
