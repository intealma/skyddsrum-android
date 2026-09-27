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
    fun nearest(shelters: List<Shelter>, lat: Double, lon: Double, count: Int = 10): List<ShelterWithDistance> =
        shelters.asSequence()
            .map { ShelterWithDistance(it, haversineMeters(lat, lon, it.lat, it.lon)) }
            .sortedBy { it.meters }
            .take(count)
            .toList()

    /** "850 m", "1.2 km", "37 km". */
    fun formatDistance(meters: Double): String = when {
        meters < 1_000 -> "${(meters / 10).roundToInt() * 10} m"
        meters < 10_000 -> "%.1f km".format(meters / 1_000)
        else -> "${(meters / 1_000).roundToInt()} km"
    }
}
