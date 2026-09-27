package io.github.intealma.skyddsrum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.PlaceSlot
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.ui.map.rememberMapView
import org.osmdroid.util.GeoPoint

@Composable
fun placeLabel(slot: PlaceSlot) = stringResource(
    when (slot) {
        PlaceSlot.HOME -> R.string.place_home
        PlaceSlot.WORK -> R.string.place_work
        PlaceSlot.SCHOOL -> R.string.place_school
    },
)

@Composable
fun SavedPlacesScreen(
    isPremium: Boolean,
    places: List<SavedPlaceInfo>,
    onUnlock: () -> Unit,
    onUseCurrentLocation: (PlaceSlot) -> Unit,
    onSetOnMap: (PlaceSlot, Double, Double) -> Unit,
    onClear: (PlaceSlot) -> Unit,
    onOpen: (ShelterWithDistance) -> Unit,
    initialMapCenter: GeoPoint,
    modifier: Modifier = Modifier,
) {
    if (!isPremium) {
        LockedPremium(onUnlock, modifier)
        return
    }
    var pickingSlot by remember { mutableStateOf<PlaceSlot?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.places_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.premium)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(16.dp)) })
            }
            Text(stringResource(R.string.places_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(places, key = { it.slot.name }) { info ->
            SavedPlaceCard(
                info = info,
                onUseCurrentLocation = { onUseCurrentLocation(info.slot) },
                onChooseOnMap = { pickingSlot = info.slot },
                onClear = { onClear(info.slot) },
                onOpen = onOpen,
            )
        }
    }

    pickingSlot?.let { slot ->
        val existing = places.firstOrNull { it.slot == slot }?.place
        MapPickerDialog(
            title = placeLabel(slot),
            start = existing?.let { GeoPoint(it.lat, it.lon) } ?: initialMapCenter,
            onPick = { lat, lon -> onSetOnMap(slot, lat, lon); pickingSlot = null },
            onDismiss = { pickingSlot = null },
        )
    }
}

@Composable
private fun SavedPlaceCard(
    info: SavedPlaceInfo,
    onUseCurrentLocation: () -> Unit,
    onChooseOnMap: () -> Unit,
    onClear: () -> Unit,
    onOpen: (ShelterWithDistance) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(placeLabel(info.slot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (info.place == null) {
                        Text(stringResource(R.string.place_not_set), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.set_place)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.set_to_my_location)) },
                            onClick = { menuOpen = false; onUseCurrentLocation() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.set_on_map)) },
                            onClick = { menuOpen = false; onChooseOnMap() })
                        if (info.place != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear_place)) },
                                onClick = { menuOpen = false; onClear() })
                        }
                    }
                }
            }
            info.nearest.forEachIndexed { i, item ->
                Spacer(Modifier.height(8.dp))
                ShelterRow(i + 1, item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun LockedPremium(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.places_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.premium_locked_body), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.unlock_premium))
        }
    }
}

/** Full-screen map with a fixed centre pin; the map centre becomes the saved place. */
@Composable
private fun MapPickerDialog(title: String, start: GeoPoint, onPick: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            val mapView = rememberMapView()
            remember(mapView) {
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(start)
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                Icon(
                    Icons.Filled.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).size(40.dp).padding(bottom = 4.dp),
                )
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.pick_on_map_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = { mapView.mapCenter.let { onPick(it.latitude, it.longitude) } },
                        modifier = Modifier.weight(2f).height(52.dp),
                    ) { Text(stringResource(R.string.save_place)) }
                }
            }
        }
    }
}
