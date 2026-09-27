package io.github.intealma.skyddsrum.data

import android.content.Context
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the bundled shelter list from assets, so the core app works fully offline. */
object ShelterRepository {

    suspend fun load(context: Context): List<Shelter> = withContext(Dispatchers.IO) {
        context.assets.open("shelters.json").bufferedReader().use { reader ->
            val json = JsonReader(reader)
            val result = ArrayList<Shelter>(64_000)
            json.beginArray()
            while (json.hasNext()) result += readShelter(json)
            json.endArray()
            result
        }
    }

    private fun readShelter(json: JsonReader): Shelter {
        var id = ""
        var address = ""
        var municipality = ""
        var lat = 0.0
        var lon = 0.0
        var capacity = 0
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "id" -> id = json.nextString()
                "address" -> address = json.nextString()
                "municipality" -> municipality = json.nextString()
                "lat" -> lat = json.nextDouble()
                "lon" -> lon = json.nextDouble()
                "capacity" -> capacity = json.nextInt()
                else -> json.skipValue()
            }
        }
        json.endObject()
        return Shelter(id, address, municipality, lat, lon, capacity)
    }

    /**
     * One entry per municipality, placed at the mean position of its shelters
     * (a good stand-in for the town centre). Used when location permission is denied.
     */
    fun municipalities(shelters: List<Shelter>): List<GeoPlace> =
        shelters.groupBy { it.municipality }
            .filterKeys { it.isNotBlank() }
            .map { (name, list) -> GeoPlace(name, list.sumOf { it.lat } / list.size, list.sumOf { it.lon } / list.size) }
            .sortedBy { it.name }
}
