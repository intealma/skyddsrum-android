package io.github.intealma.skyddsrum.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime

/** One row of the website's public `shelter_status` view: live aggregates of active check-ins. */
data class ShelterStatus(
    val shelterId: String,
    val checkinCount: Int = 0,
    val occupancy: Int = 0,
    val adults: Int = 0,
    val children: Int = 0,
    val specialNeeds: Int = 0,
    val medical: Int = 0,
    val leadership: Int = 0,
    val technical: Int = 0,
    val community: Int = 0,
    val volunteer: Int = 0,
    val waterNone: Int = 0,
    val water1to3: Int = 0,
    val water4plus: Int = 0,
    val foodNone: Int = 0,
    val food1day: Int = 0,
    val food2plus: Int = 0,
    val powerBanks: Int = 0,
    val firstAidKits: Int = 0,
    val radios: Int = 0,
    val flashlights: Int = 0,
) {
    /** Point estimates like the website: "1–3 L" counts as 2 L, "4 L+" as 4 L; food in days. */
    val waterLitres: Int get() = water1to3 * 2 + water4plus * 4
    val foodDays: Int get() = food1day + food2plus * 2
}

/** One check-in event from the public `shelter_activity` view (headcount + time, nothing personal). */
data class ActivityEvent(val shelterId: String, val atMillis: Long, val people: Int)

data class LiveSnapshot(val statuses: Map<String, ShelterStatus>, val recent: List<ActivityEvent>, val fetchedAtMillis: Long) {
    val totalPeople: Int get() = statuses.values.sumOf { it.occupancy }
    val activeShelters: Int get() = statuses.values.count { it.occupancy > 0 }
}

enum class ResourceLevel { NO_DATA, LOW, MODERATE, GOOD }

/** Same rules as the website's dashboard. */
object LiveMath {
    /** Low/Moderate/Good from the share of check-ins reporting none vs plenty. */
    fun resourceLevel(none: Int, mid: Int, good: Int): ResourceLevel {
        val total = none + mid + good
        return when {
            total == 0 -> ResourceLevel.NO_DATA
            none.toDouble() / total >= 0.5 -> ResourceLevel.LOW
            good.toDouble() / total >= 0.5 -> ResourceLevel.GOOD
            else -> ResourceLevel.MODERATE
        }
    }

    data class Bucket(val latestMillis: Long, val people: Int)

    /** Groups arrivals close in time into one line (newest first), so 50 arrivals read as a few rows. */
    fun bucketActivity(events: List<ActivityEvent>, bucketMinutes: Int = 10): List<Bucket> {
        val bucketMs = bucketMinutes * 60_000L
        val out = ArrayList<Bucket>()
        for (e in events.sortedByDescending { it.atMillis }) {
            val last = out.lastOrNull()
            if (last != null && last.latestMillis - e.atMillis < bucketMs) {
                out[out.lastIndex] = last.copy(people = last.people + e.people)
            } else {
                out += Bucket(e.atMillis, e.people)
            }
        }
        return out
    }

    fun isFull(status: ShelterStatus?, capacity: Int) = capacity > 0 && (status?.occupancy ?: 0) >= capacity

    fun parseStatuses(body: String): List<ShelterStatus> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun n(k: String) = o.optInt(k, 0)
            ShelterStatus(
                shelterId = o.getString("shelter_id"),
                checkinCount = n("checkin_count"), occupancy = n("current_occupancy"),
                adults = n("adults_count"), children = n("children_count"), specialNeeds = n("special_needs_count"),
                medical = n("medical_count"), leadership = n("leadership_count"), technical = n("technical_count"),
                community = n("community_count"), volunteer = n("volunteer_count"),
                waterNone = n("water_none_count"), water1to3 = n("water_1_3l_count"), water4plus = n("water_4l_plus_count"),
                foodNone = n("food_none_count"), food1day = n("food_1day_count"), food2plus = n("food_2plus_count"),
                powerBanks = n("power_bank_count"), firstAidKits = n("first_aid_kit_count"),
                radios = n("radio_count"), flashlights = n("flashlight_count"),
            )
        }
    }

    fun parseActivity(body: String): List<ActivityEvent> {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val at = runCatching { OffsetDateTime.parse(o.getString("checked_in_at")).toInstant().toEpochMilli() }.getOrNull()
                ?: return@mapNotNull null
            ActivityEvent(o.optString("shelter_id"), at, o.optInt("people_count", 1))
        }
    }
}

/**
 * Read-only access to the website's live check-in data (Supabase PostgREST, public anon key; the
 * views are publicly readable by design and contain only aggregates). Checking in happens on the website.
 */
class LiveRepository(private val baseUrl: String, private val anonKey: String, private val userAgent: String) {

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    /** All shelters with active check-ins, plus the latest arrivals anywhere. Null if unavailable. */
    suspend fun snapshot(): LiveSnapshot? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val statuses = LiveMath.parseStatuses(get("shelter_status?select=*&limit=5000"))
            val recent = LiveMath.parseActivity(get("shelter_activity?select=*&order=checked_in_at.desc&limit=20"))
            LiveSnapshot(statuses.associateBy { it.shelterId }, recent, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Live status unavailable: ${e.message}")
            null
        }
    }

    /** Recent arrivals at one shelter (for its dashboard). */
    suspend fun activity(shelterId: String): List<ActivityEvent>? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        try {
            val id = URLEncoder.encode(shelterId, "UTF-8")
            LiveMath.parseActivity(get("shelter_activity?select=*&shelter_id=eq.$id&order=checked_in_at.desc&limit=50"))
        } catch (e: Exception) {
            Log.w(TAG, "Activity unavailable: ${e.message}")
            null
        }
    }

    private fun get(pathAndQuery: String): String {
        val conn = URL("${baseUrl.trimEnd('/')}/rest/v1/$pathAndQuery").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Authorization", "Bearer $anonKey")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", userAgent)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TAG = "Live"
    }
}
