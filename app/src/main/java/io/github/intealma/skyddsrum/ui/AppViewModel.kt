package io.github.intealma.skyddsrum.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.GeoPlace
import io.github.intealma.skyddsrum.data.LocationProvider
import io.github.intealma.skyddsrum.data.PlaceSlot
import io.github.intealma.skyddsrum.data.SavedPlacesStore
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.data.ShelterRepository
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.premium.Premium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where distances are measured from: the device position or a chosen municipality. */
data class Origin(val place: GeoPlace, val isDeviceLocation: Boolean)

/** A request to move the map camera; [nonce] makes repeated requests for the same spot distinct. */
data class MapFocus(val lat: Double, val lon: Double, val zoom: Double, val nonce: Long = System.nanoTime())

data class SavedPlaceInfo(val slot: PlaceSlot, val place: GeoPlace?, val nearest: List<ShelterWithDistance>)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val location = LocationProvider(app)
    private val savedStore = SavedPlacesStore(app)

    private val _shelters = MutableStateFlow<List<Shelter>?>(null)
    /** Null while the bundled data is loading. */
    val shelters: StateFlow<List<Shelter>?> = _shelters.asStateFlow()

    private val _municipalities = MutableStateFlow<List<GeoPlace>>(emptyList())
    val municipalities: StateFlow<List<GeoPlace>> = _municipalities.asStateFlow()

    private val _origin = MutableStateFlow<Origin?>(null)
    val origin: StateFlow<Origin?> = _origin.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /** String resource id of a status message for the home screen, or null. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    private val _selected = MutableStateFlow<ShelterWithDistance?>(null)
    /** Shelter shown in the detail sheet. */
    val selected: StateFlow<ShelterWithDistance?> = _selected.asStateFlow()

    private val _highlightedId = MutableStateFlow<String?>(null)
    /** Shelter highlighted on the map; stays after the detail sheet closes. */
    val highlightedId: StateFlow<String?> = _highlightedId.asStateFlow()

    private val _mapFocus = MutableStateFlow<MapFocus?>(null)
    val mapFocus: StateFlow<MapFocus?> = _mapFocus.asStateFlow()

    val isPremium: StateFlow<Boolean> = Premium.isPremium

    val nearest: StateFlow<List<ShelterWithDistance>> =
        combine(_shelters, _origin) { list, origin ->
            if (list == null || origin == null) emptyList()
            else Geo.nearest(list, origin.place.lat, origin.place.lon, 10)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedPlaces: StateFlow<List<SavedPlaceInfo>> =
        combine(_shelters, savedStore.places) { list, places ->
            PlaceSlot.entries.map { slot ->
                val place = places[slot]
                val near = if (list != null && place != null) Geo.nearest(list, place.lat, place.lon, 3) else emptyList()
                SavedPlaceInfo(slot, place, near)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, PlaceSlot.entries.map { SavedPlaceInfo(it, null, emptyList()) })

    init {
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val list = ShelterRepository.load(getApplication())
            Log.i("Skyddsrum", "Loaded ${list.size} shelters in ${System.currentTimeMillis() - started} ms")
            _shelters.value = list
            _municipalities.value = ShelterRepository.municipalities(list)
        }
    }

    fun hasLocationPermission() = location.hasPermission()

    /**
     * Gets the device position and makes it the origin. With [openClosest], the closest shelter
     * opens right away: the one-tap flow. Returns false (via [onFailed]) if no fix was available.
     */
    fun locate(openClosest: Boolean, onFailed: () -> Unit = {}) {
        if (_locating.value) return
        viewModelScope.launch {
            _locating.value = true
            _message.value = null
            val fix = location.currentLocation()
            _locating.value = false
            if (fix == null) {
                _message.value = R.string.location_unavailable
                onFailed()
                return@launch
            }
            _origin.value = Origin(GeoPlace("", fix.latitude, fix.longitude), isDeviceLocation = true)
            if (openClosest) openClosest()
        }
    }

    fun chooseMunicipality(place: GeoPlace, openClosest: Boolean) {
        _message.value = null
        _origin.value = Origin(place, isDeviceLocation = false)
        if (openClosest) openClosest()
    }

    fun onPermissionDenied() {
        _message.value = R.string.permission_denied
    }

    /** Opens the detail sheet for the closest shelter to the current origin. */
    fun openClosest() {
        val list = _shelters.value ?: return
        val origin = _origin.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            Geo.nearest(list, origin.place.lat, origin.place.lon, 1).firstOrNull()?.let(::select)
        }
    }

    fun select(shelter: Shelter) {
        val o = _origin.value?.place
        val meters = if (o != null) Geo.haversineMeters(o.lat, o.lon, shelter.lat, shelter.lon) else Double.NaN
        select(ShelterWithDistance(shelter, meters))
    }

    fun select(item: ShelterWithDistance) {
        _selected.value = item
        _highlightedId.value = item.shelter.id
    }

    fun dismissSelected() {
        _selected.value = null
    }

    fun focusMapOn(shelter: Shelter) {
        _mapFocus.value = MapFocus(shelter.lat, shelter.lon, 17.0)
        _highlightedId.value = shelter.id
    }

    fun onMapFocusConsumed() {
        _mapFocus.value = null
    }

    fun setSavedPlace(slot: PlaceSlot, lat: Double, lon: Double) = savedStore.set(slot, lat, lon)

    fun clearSavedPlace(slot: PlaceSlot) = savedStore.clear(slot)

    /** Saves the device position as [slot]; calls [onFailed] if there is no fix. */
    fun setSavedPlaceToCurrentLocation(slot: PlaceSlot, onFailed: () -> Unit) {
        viewModelScope.launch {
            _locating.value = true
            val fix = location.currentLocation()
            _locating.value = false
            if (fix != null) savedStore.set(slot, fix.latitude, fix.longitude) else onFailed()
        }
    }
}
