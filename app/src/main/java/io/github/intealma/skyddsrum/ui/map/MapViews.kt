package io.github.intealma.skyddsrum.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/** Center of Sweden, used before we know where the user is. */
val SWEDEN_CENTER = GeoPoint(62.0, 16.0)

fun createMapView(context: Context): MapView = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    isTilesScaledToDpi = true
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    minZoomLevel = 4.0
    maxZoomLevel = 19.0
    isVerticalMapRepetitionEnabled = false
    // OpenStreetMap attribution is required by the tile usage policy and the ODbL.
    overlays += CopyrightOverlay(context)
    controller.setZoom(5.0)
    controller.setCenter(SWEDEN_CENTER)
}

/** A MapView tied to the Compose lifecycle (resume/pause/detach). */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    return mapView
}
