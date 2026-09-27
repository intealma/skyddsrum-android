package io.github.intealma.skyddsrum.data

/** One public shelter (skyddsrum) from the bundled open dataset. */
data class Shelter(
    val id: String,
    val address: String,
    val municipality: String,
    val lat: Double,
    val lon: Double,
    val capacity: Int,
)

data class ShelterWithDistance(val shelter: Shelter, val meters: Double)

/** A named point on the map: a municipality, the user's position or a saved place. */
data class GeoPlace(val name: String, val lat: Double, val lon: Double)
