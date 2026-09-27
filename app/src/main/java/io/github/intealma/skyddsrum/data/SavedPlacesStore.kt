package io.github.intealma.skyddsrum.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaceSlot { HOME, WORK, SCHOOL }

/** Premium feature storage: one saved position per [PlaceSlot], kept on the device only. */
class SavedPlacesStore(context: Context) {
    private val prefs = context.getSharedPreferences("saved_places", Context.MODE_PRIVATE)
    private val _places = MutableStateFlow(read())
    val places: StateFlow<Map<PlaceSlot, GeoPlace>> = _places.asStateFlow()

    fun set(slot: PlaceSlot, lat: Double, lon: Double) {
        prefs.edit()
            .putString("${slot.name}_lat", lat.toString())
            .putString("${slot.name}_lon", lon.toString())
            .apply()
        _places.value = read()
    }

    fun clear(slot: PlaceSlot) {
        prefs.edit().remove("${slot.name}_lat").remove("${slot.name}_lon").apply()
        _places.value = read()
    }

    private fun read(): Map<PlaceSlot, GeoPlace> = PlaceSlot.entries.mapNotNull { slot ->
        val lat = prefs.getString("${slot.name}_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("${slot.name}_lon", null)?.toDoubleOrNull()
        if (lat != null && lon != null) slot to GeoPlace(slot.name, lat, lon) else null
    }.toMap()
}
