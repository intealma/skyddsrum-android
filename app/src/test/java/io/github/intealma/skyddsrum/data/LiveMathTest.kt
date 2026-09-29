package io.github.intealma.skyddsrum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMathTest {

    @Test
    fun resourceLevelsMatchWebsite() {
        assertEquals(ResourceLevel.NO_DATA, LiveMath.resourceLevel(0, 0, 0))
        assertEquals(ResourceLevel.LOW, LiveMath.resourceLevel(2, 1, 1))
        assertEquals(ResourceLevel.GOOD, LiveMath.resourceLevel(0, 1, 1))
        assertEquals(ResourceLevel.MODERATE, LiveMath.resourceLevel(1, 2, 1))
    }

    @Test
    fun arrivalsCloseInTimeAreGrouped() {
        val min = 60_000L
        val events = listOf(
            ActivityEvent("a", 100 * min, 2),
            ActivityEvent("a", 95 * min, 1), // within 10 min of the previous: same bucket
            ActivityEvent("a", 60 * min, 4),
        )
        val buckets = LiveMath.bucketActivity(events)
        assertEquals(listOf(3, 4), buckets.map { it.people })
    }

    @Test
    fun parsesPostgrestRows() {
        val statuses = LiveMath.parseStatuses(
            """[{"shelter_id":"187608-0","checkin_count":2,"current_occupancy":5,"adults_count":3,"children_count":2,
               "medical_count":1,"water_1_3l_count":1,"water_4l_plus_count":1,"food_2plus_count":1}]""",
        )
        val s = statuses.single()
        assertEquals(5, s.occupancy)
        assertEquals(6, s.waterLitres)
        assertEquals(2, s.foodDays)
        assertTrue(LiveMath.isFull(s, 5))
        assertFalse(LiveMath.isFull(s, 60))

        val events = LiveMath.parseActivity("""[{"shelter_id":"187608-0","checked_in_at":"2026-09-29T12:34:56.123456+00:00","people_count":3}]""")
        assertEquals(3, events.single().people)
    }
}
