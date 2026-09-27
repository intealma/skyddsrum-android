package io.github.intealma.skyddsrum.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun haversine_linkopingToStockholm() {
        // Linköping (58.4108, 15.6214) to Stockholm (59.3293, 18.0686): ~172 km straight line.
        val m = Geo.haversineMeters(58.4108, 15.6214, 59.3293, 18.0686)
        assertEquals(172_000.0, m, 3_000.0)
    }

    @Test
    fun haversine_samePointIsZero() {
        assertEquals(0.0, Geo.haversineMeters(58.41, 15.62, 58.41, 15.62), 1e-6)
    }

    @Test
    fun nearest_sortsByDistanceAndLimits() {
        val shelters = listOf(
            Shelter("far", "", "", 58.50, 15.62, 10),
            Shelter("near", "", "", 58.411, 15.6214, 10),
            Shelter("mid", "", "", 58.42, 15.6214, 10),
        )
        val result = Geo.nearest(shelters, 58.4108, 15.6214, count = 2)
        assertEquals(listOf("near", "mid"), result.map { it.shelter.id })
    }

    @Test
    fun formatDistance_usesMetersThenKilometers() {
        assertEquals("850 m", Geo.formatDistance(847.0))
        assertEquals("1.2 km", Geo.formatDistance(1_234.0).replace(',', '.'))
        assertEquals("37 km", Geo.formatDistance(37_400.0))
    }
}
