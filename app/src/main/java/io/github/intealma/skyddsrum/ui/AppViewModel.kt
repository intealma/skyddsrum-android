package io.github.intealma.skyddsrum.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.intealma.skyddsrum.BuildConfig
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.SkyddsrumApp
import io.github.intealma.skyddsrum.data.LatLon
import io.github.intealma.skyddsrum.data.Route
import io.github.intealma.skyddsrum.data.RoutedShelter
import io.github.intealma.skyddsrum.data.Router
import io.github.intealma.skyddsrum.data.TravelMode
import io.github.intealma.skyddsrum.data.ActivityEvent
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.LiveMath
import io.github.intealma.skyddsrum.data.LiveRepository
import io.github.intealma.skyddsrum.data.LiveSnapshot
import io.github.intealma.skyddsrum.data.GeoPlace
import io.github.intealma.skyddsrum.data.LocationProvider
import io.github.intealma.skyddsrum.data.PlaceSlot
import io.github.intealma.skyddsrum.data.SavedPlacesStore
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.data.ShelterRepository
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.premium.Premium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where distances are measured from: the device position or a chosen municipality. */
data class Origin(val place: GeoPlace, val isDeviceLocation: Boolean)

/** A request to move the map camera; [nonce] makes repeated requests for the same spot distinct. */
data class MapFocus(val lat: Double, val lon: Double, val zoom: Double, val nonce: Long = System.nanoTime())

data class SavedPlaceInfo(val slot: PlaceSlot, val place: GeoPlace?, val nearest: List<ShelterWithDistance>)

/** What the detail sheet shows: a shelter, plus its route when opened from the route list. */
data class Selection(val item: ShelterWithDistance, val route: Route? = null, val mode: TravelMode? = null)

/**
 * The "3 best shelters by travel time" panel. [items] is the current ranking (may be interim, without
 * route lines); [lines] are the drawn route lines, which keep showing the previous result until new
 * geometry arrives, like the website. [linesVersion] changes whenever new lines should animate in.
 */
data class RoutesUi(
    val origin: LatLon,
    val mode: TravelMode,
    val loading: Boolean,
    val items: List<RoutedShelter>,
    val lines: List<List<LatLon>>,
    val linesVersion: Int,
    val skippedFull: Int = 0,
)

private const val LIVE_REFRESH_MS = 30_000L

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

    private val _selectedIdFlow = MutableStateFlow<String?>(null)
    private val _selected = MutableStateFlow<Selection?>(null)
    /** Shelter shown in the detail sheet. */
    val selected: StateFlow<Selection?> = _selected.asStateFlow()

    private val router = Router(
        userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (+${SkyddsrumApp.SOURCE_URL})",
        trafiklabKey = BuildConfig.TRAFIKLAB_API_KEY,
    )
    private val live = LiveRepository(
        baseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}",
    )
    val liveConfigured: Boolean get() = live.isConfigured

    /**
     * Live check-in status from the website's backend, refreshed every 30 s while the UI is visible.
     * Null = not loaded or unavailable (offline, project paused); the app works without it.
     */
    val liveSnapshot: StateFlow<LiveSnapshot?> = flow {
        while (true) {
            emit(live.snapshot())
            delay(LIVE_REFRESH_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Recent arrivals at the shelter open in the detail sheet. */
    val selectedActivity: StateFlow<List<ActivityEvent>?> = MutableStateFlow<List<ActivityEvent>?>(null).let { out ->
        viewModelScope.launch {
            _selectedIdFlow.collectLatest { id ->
                out.value = null
                if (id != null) out.value = live.activity(id)
            }
        }
        out.asStateFlow()
    }

    private val _routes = MutableStateFlow<RoutesUi?>(null)
    val routes: StateFlow<RoutesUi?> = _routes.asStateFlow()
    private var routesJob: Job? = null

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

    private val byId: Map<String, Shelter> by lazy { _shelters.value.orEmpty().associateBy { it.id } }

    /** Address for a shelter id (for live arrivals); null until the data has loaded. */
    fun addressOf(id: String): String? = if (_shelters.value == null) null else byId[id]?.address

    fun hasLocationPermission() = location.hasPermission()

    /**
     * Gets the device position and makes it the origin. With [showRoutes], the 3 best shelters by
     * travel time are routed right away (the one-tap flow). Calls [onFailed] if no fix was available.
     */
    fun locate(showRoutes: Boolean, onFailed: () -> Unit = {}) {
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
            if (showRoutes) showRoutes()
        }
    }

    fun chooseMunicipality(place: GeoPlace, showRoutes: Boolean) {
        _message.value = null
        _origin.value = Origin(place, isDeviceLocation = false)
        if (showRoutes) showRoutes()
    }

    fun onPermissionDenied() {
        _message.value = R.string.permission_denied
    }

    /** Starts the route panel from the current origin, walking first (as on the website). */
    fun showRoutes() {
        val o = _origin.value?.place ?: return
        val origin = LatLon(o.lat, o.lon)
        val keepMode = _routes.value?.takeIf { it.origin == origin }?.mode
        _routes.value = null
        setTravelMode(keepMode ?: TravelMode.WALK, prefetchOthers = keepMode == null)
    }

    /** Ranks and routes the 3 best shelters for [mode]; the list shows first, the lines follow. */
    fun setTravelMode(mode: TravelMode, prefetchOthers: Boolean = false) {
        val list = _shelters.value ?: return
        val o = _origin.value?.place ?: return
        val origin = LatLon(o.lat, o.lon)
        val previous = _routes.value?.takeIf { it.origin == origin }
        _routes.value = RoutesUi(
            origin, mode, loading = true, items = emptyList(),
            lines = previous?.lines ?: emptyList(), linesVersion = previous?.linesVersion ?: 0,
        )
        routesJob?.cancel()
        routesJob = viewModelScope.launch {
            val statuses = liveSnapshot.value?.statuses.orEmpty()
            val isFull: (Shelter) -> Boolean = { LiveMath.isFull(statuses[it.id], it.capacity) }
            val final = router.best3(mode, origin, list, isFull) { interim ->
                _routes.update { cur ->
                    cur?.takeIf { it.mode == mode }?.copy(loading = false, items = interim.items, skippedFull = interim.skippedFull) ?: cur
                }
            }
            _routes.update { cur ->
                cur?.takeIf { it.mode == mode }?.copy(
                    loading = false,
                    items = final.items,
                    lines = final.items.map { it.route.points },
                    linesVersion = cur.linesVersion + 1,
                    skippedFull = final.skippedFull,
                ) ?: cur
            }
            if (prefetchOthers) router.prefetch(mode, origin, list, isFull)
        }
    }

    fun clearRoutes() {
        routesJob?.cancel()
        _routes.value = null
    }

    fun select(routed: RoutedShelter, mode: TravelMode) {
        val o = _origin.value?.place
        val meters = if (o != null) Geo.haversineMeters(o.lat, o.lon, routed.shelter.lat, routed.shelter.lon) else Double.NaN
        _selected.value = Selection(ShelterWithDistance(routed.shelter, meters), routed.route, mode)
        _selectedIdFlow.value = routed.shelter.id
        _highlightedId.value = routed.shelter.id
        _mapFocus.value = MapFocus(routed.shelter.lat, routed.shelter.lon, 16.0)
    }

    fun select(shelter: Shelter) {
        val o = _origin.value?.place
        val meters = if (o != null) Geo.haversineMeters(o.lat, o.lon, shelter.lat, shelter.lon) else Double.NaN
        select(ShelterWithDistance(shelter, meters))
    }

    fun select(item: ShelterWithDistance) {
        _selected.value = Selection(item)
        _selectedIdFlow.value = item.shelter.id
        _highlightedId.value = item.shelter.id
    }

    fun dismissSelected() {
        _selected.value = null
        _selectedIdFlow.value = null
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
