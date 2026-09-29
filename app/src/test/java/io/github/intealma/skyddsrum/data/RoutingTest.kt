package io.github.intealma.skyddsrum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {

    @Test
    fun parseIsoDuration() {
        assertEquals(25.0, Router.parseIsoDurationMin("PT25M")!!, 1e-9)
        assertEquals(84.5, Router.parseIsoDurationMin("PT1H24M30S")!!, 1e-9)
        assertNull(Router.parseIsoDurationMin("not a duration"))
    }

    @Test
    fun originKeyRoundsToAbout11Meters() {
        assertEquals(Router.originKey(LatLon(58.41081, 15.62141)), Router.originKey(LatLon(58.41083, 15.62143)))
    }

    @Test
    fun directLineRanksAt15MinutesPerKm() {
        val r = Router.directLine(LatLon(58.4108, 15.6214), LatLon(58.4198, 15.6214), RouteNote.DIRECT_LINE)
        assertEquals(1.0, r.distanceKm, 0.01)
        assertEquals(15.0, r.rankMinutes, 0.2)
    }

    @Test
    fun parsesRealResRobotTrip() {
        // Recorded answer: Linköping centre to 58.395,15.570 (walk, bus BLT 12, walk; 23 min).
        val body = javaClass.getResource("/resrobot_trip.json")!!.readText()
        val route = Router.parseTrip(body)
        assertEquals(23.0, route.durationMin!!, 1e-9)
        val steps = route.steps!!
        assertEquals(3, steps.size)
        assertTrue(steps[0] is TransitStep.Walk)
        val ride = steps[1] as TransitStep.Ride
        assertEquals("BLT 12", ride.line)
        assertTrue(ride.towards!!.startsWith("Änggårdsskolan"))
        assertEquals("13:45", ride.dep)
        assertTrue(route.points.size >= 4)
        assertTrue(route.distanceKm in 2.0..8.0)
    }
}
