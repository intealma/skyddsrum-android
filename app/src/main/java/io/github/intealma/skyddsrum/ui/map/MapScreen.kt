package io.github.intealma.skyddsrum.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.ui.MapFocus
import io.github.intealma.skyddsrum.ui.Origin
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    shelters: List<Shelter>,
    origin: Origin?,
    focus: MapFocus?,
    selectedId: String?,
    hasLocationPermission: Boolean,
    onShelterTap: (Shelter) -> Unit,
    onMyLocation: () -> Unit,
    onFocusConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapView()
    val density = LocalDensity.current.density
    val currentOnTap by rememberUpdatedState(onShelterTap)

    val clusters = remember(shelters) {
        ShelterClusterOverlay(shelters, density) { currentOnTap(it) }.also { mapView.overlays += it }
    }
    val myLocation = remember(hasLocationPermission) {
        if (!hasLocationPermission) null
        else MyLocationNewOverlay(GpsMyLocationProvider(mapView.context), mapView).also {
            it.enableMyLocation()
            mapView.overlays += it
        }
    }

    // First time the map is shown: jump to a pending focus, else to the origin.
    LaunchedEffect(Unit) {
        val start = focus ?: origin?.let { MapFocus(it.place.lat, it.place.lon, 14.0) } ?: return@LaunchedEffect
        mapView.controller.setZoom(start.zoom)
        mapView.controller.setCenter(GeoPoint(start.lat, start.lon))
    }
    // Later focus requests (e.g. "Show on map") animate; each request is used once.
    LaunchedEffect(focus) {
        focus ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(focus.lat, focus.lon), focus.zoom, 600L)
        onFocusConsumed()
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                clusters.selectedId = selectedId
                it.invalidate()
            },
        )
        SmallFloatingActionButton(
            onClick = {
                val fix = myLocation?.myLocation
                if (fix != null) mapView.controller.animateTo(fix, 15.0, 600L)
                else origin?.let { mapView.controller.animateTo(GeoPoint(it.place.lat, it.place.lon), 14.0, 600L) }
                onMyLocation()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.my_location))
        }
    }
}
