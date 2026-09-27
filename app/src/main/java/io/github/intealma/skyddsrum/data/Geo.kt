package io.github.intealma.skyddsrum.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters (Haversine formula). */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** The [count] shelters closest to ([lat], [lon]), nearest first. */
    fun nearest(shelters: List<Shelter>, lat: Double, lon: Double, count: Int = 10): List<ShelterWithDistance> {
        val k = minOf(count, shelters.size)
        if (k <= 0) return emptyList()
        // One pass with a cheap equirectangular distance keeps the k best (no sort of all 63k);
        // exact Haversine distances are then computed for those k only.
        val cosLat = cos(Math.toRadians(lat))
        val bestIdx = IntArray(k) { -1 }
        val bestD = DoubleArray(k) { Double.MAX_VALUE }
        for (i in shelters.indices) {
            val s = shelters[i]
            val dx = (s.lon - lon) * cosLat
            val dy = s.lat - lat
            val d = dx * dx + dy * dy
            if (d >= bestD[k - 1]) continue
            var j = k - 1
            while (j > 0 && bestD[j - 1] > d) {
                bestD[j] = bestD[j - 1]
                bestIdx[j] = bestIdx[j - 1]
                j--
            }
            bestD[j] = d
            bestIdx[j] = i
        }
        return bestIdx.filter { it >= 0 }
            .map { ShelterWithDistance(shelters[it], haversineMeters(lat, lon, shelters[it].lat, shelters[it].lon)) }
            .sortedBy { it.meters }
    }

    /** "850 m", "1.2 km", "37 km". */
    fun formatDistance(meters: Double): String = when {
        meters < 1_000 -> "${(meters / 10).roundToInt() * 10} m"
        meters < 10_000 -> "%.1f km".format(meters / 1_000)
        else -> "${(meters / 1_000).roundToInt()} km"
    }
}
