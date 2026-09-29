package io.github.intealma.skyddsrum.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.LatLon
import io.github.intealma.skyddsrum.data.RouteNote
import io.github.intealma.skyddsrum.data.RoutedShelter
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.data.TravelMode
import io.github.intealma.skyddsrum.ui.MapFocus
import io.github.intealma.skyddsrum.ui.Origin
import io.github.intealma.skyddsrum.ui.RoutesUi
import io.github.intealma.skyddsrum.ui.panel
import io.github.intealma.skyddsrum.ui.theme.Field
import io.github.intealma.skyddsrum.ui.theme.LineStrong
import io.github.intealma.skyddsrum.ui.theme.Red
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted
import io.github.intealma.skyddsrum.ui.theme.White
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    shelters: List<Shelter>,
    origin: Origin?,
    focus: MapFocus?,
    selectedId: String?,
    routes: RoutesUi?,
    locating: Boolean,
    onShelterTap: (Shelter) -> Unit,
    onLocate: () -> Unit,
    onModeChange: (TravelMode) -> Unit,
    onRouteTap: (RoutedShelter, TravelMode) -> Unit,
    onCloseRoutes: () -> Unit,
    onFocusConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapView()
    val density = LocalDensity.current.density
    val currentOnTap by rememberUpdatedState(onShelterTap)
    val typeface = remember { ResourcesCompat.getFont(mapView.context, R.font.nunito_sans) }

    val clusters = remember(shelters) {
        ShelterClusterOverlay(shelters, density, typeface) { currentOnTap(it) }.also { mapView.overlays += it }
    }
    val routesOverlay = remember { RoutesOverlay(density, typeface).also { mapView.overlays += it } }

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
    // New route origin: fly there (like the website); new route lines: fit them below the panel.
    LaunchedEffect(routes?.origin) {
        routes?.origin?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon), 14.0, 600L) }
    }
    LaunchedEffect(routes?.linesVersion) {
        val r = routes ?: return@LaunchedEffect
        if (r.linesVersion == 0) return@LaunchedEffect
        fitRoutes(mapView, r)
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                clusters.selectedId = selectedId
                routesOverlay.set(
                    origin = routes?.origin,
                    targets = routes?.items?.map { r -> LatLon(r.shelter.lat, r.shelter.lon) } ?: emptyList(),
                    lines = routes?.lines ?: emptyList(),
                    linesVersion = routes?.linesVersion ?: -1,
                )
                it.invalidate()
            },
        )

        if (routes != null || locating) {
            RoutesPanel(
                routes = routes,
                locating = locating,
                onModeChange = onModeChange,
                onRouteTap = onRouteTap,
                onClose = onCloseRoutes,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            )
        }

        LocateButton(
            active = routes != null,
            onClick = onLocate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/** Mode bar + the 3 best shelters, like the website's locate panel. */
@Composable
private fun RoutesPanel(
    routes: RoutesUi?,
    locating: Boolean,
    onModeChange: (TravelMode) -> Unit,
    onRouteTap: (RoutedShelter, TravelMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = locating || routes == null || routes.loading
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).panel().padding(4.dp).alpha(if (loading) 0.55f else 1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TravelMode.entries.forEach { mode ->
                    val active = routes?.mode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) Red else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = routes != null && !active) { onModeChange(mode) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(modeLabel(mode)).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) White else TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).panel().clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val w = size.minDimension
                    drawLine(White, Offset(0f, 0f), Offset(w, w), w * 0.18f)
                    drawLine(White, Offset(w, 0f), Offset(0f, w), w * 0.18f)
                }
            }
        }
        if (routes == null || (loading && routes.items.isEmpty())) {
            Text(
                stringResource(if (locating || routes == null) R.string.locating else R.string.routes_loading),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.fillMaxWidth().panel().padding(12.dp),
            )
        } else {
            routes.items.forEachIndexed { i, item -> RouteItem(i + 1, item) { onRouteTap(item, routes.mode) } }
        }
    }
}

@Composable
private fun RouteItem(rank: Int, item: RoutedShelter, onClick: () -> Unit) {
    val r = item.route
    val parts = buildList {
        add(Geo.formatDistance(r.distanceKm * 1000))
        r.durationMin?.let { add(stringResource(R.string.minutes, max(1, it.roundToInt()))) }
        when (r.note) {
            RouteNote.TRANSIT_NO_KEY -> add(stringResource(R.string.transit_no_key))
            RouteNote.TRANSIT_FAILED -> add(stringResource(R.string.transit_failed))
            RouteNote.DIRECT_LINE -> add(stringResource(R.string.direct_line))
            RouteNote.WALK_NO_TRANSIT -> add(stringResource(R.string.walk_no_transit))
            null -> Unit
        }
    }
    Row(
        Modifier.fillMaxWidth().panel().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
            Text("$rank", style = MaterialTheme.typography.labelMedium, color = White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.shelter.address, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (r.approx) TextFaint else TextMuted)
        }
    }
}

fun modeLabel(mode: TravelMode) = when (mode) {
    TravelMode.WALK -> R.string.mode_walk
    TravelMode.BIKE -> R.string.mode_bike
    TravelMode.CAR -> R.string.mode_car
    TravelMode.TRANSIT -> R.string.mode_transit
}

/** Square locate button with a crosshair, like the website's; white when routes are showing. */
@Composable
private fun LocateButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.find_nearest)
    val fg = if (active) androidx.compose.ui.graphics.Color.Black else White
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) White else Field)
            .border(1.dp, if (active) White else LineStrong, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.minDimension
            val stroke = w * 0.09f
            drawCircle(fg, radius = w * 0.14f, style = Stroke(stroke))
            val a = w * 0.08f
            val b = w * 0.22f
            drawLine(fg, Offset(w / 2, a), Offset(w / 2, b), stroke)
            drawLine(fg, Offset(w / 2, w - b), Offset(w / 2, w - a), stroke)
            drawLine(fg, Offset(a, w / 2), Offset(b, w / 2), stroke)
            drawLine(fg, Offset(w - b, w / 2), Offset(w - a, w / 2), stroke)
        }
    }
}

/** Zooms to the origin + routes, leaving room at the top for the routes panel. */
private fun fitRoutes(mapView: org.osmdroid.views.MapView, r: RoutesUi) {
    val pts = r.lines.flatten() + r.origin
    if (pts.size < 2) return
    val north = pts.maxOf { it.lat }
    val south = pts.minOf { it.lat }
    val east = pts.maxOf { it.lon }
    val west = pts.minOf { it.lon }
    val latSpan = max(north - south, 0.002)
    val lonSpan = max(east - west, 0.002)
    // The panel covers roughly the top 40 % of the map, so extend the box upwards.
    val box = BoundingBox(north + latSpan * 0.9, east + lonSpan * 0.15, south - latSpan * 0.15, west - lonSpan * 0.15)
    mapView.post { mapView.zoomToBoundingBox(box, true, 0, 17.0, 700L) }
}
