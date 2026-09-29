package io.github.intealma.skyddsrum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.PlaceSlot
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.ui.map.rememberMapView
import io.github.intealma.skyddsrum.ui.theme.Black
import io.github.intealma.skyddsrum.ui.theme.Red
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.places_title).uppercase(), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.places_subtitle), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        items(places, key = { it.slot.name }) { info ->
            SavedPlaceSection(
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
private fun SavedPlaceSection(
    info: SavedPlaceInfo,
    onUseCurrentLocation: () -> Unit,
    onChooseOnMap: () -> Unit,
    onClear: () -> Unit,
    onOpen: (ShelterWithDistance) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(placeLabel(info.slot), Modifier.weight(1f))
            Box {
                Text(
                    stringResource(R.string.set_place).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.clickable { menuOpen = true }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
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
        Spacer(Modifier.height(6.dp))
        if (info.place == null) {
            Text(
                stringResource(R.string.place_not_set),
                style = MaterialTheme.typography.bodySmall,
                color = TextFaint,
                modifier = Modifier.fillMaxWidth().panel().padding(12.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info.nearest.forEachIndexed { i, item -> ShelterRow(i + 1, item, onClick = { onOpen(item) }) }
            }
        }
    }
}

@Composable
private fun LockedPremium(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShelterMark(40.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.places_title).uppercase(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.premium_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextMuted,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.unlock_premium), onClick = onUnlock, modifier = Modifier.fillMaxWidth())
    }
}

/** Full-screen map with a fixed centre dot; the map centre becomes the saved place. */
@Composable
private fun MapPickerDialog(title: String, start: GeoPoint, onPick: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Black)) {
            val mapView = rememberMapView()
            remember(mapView) {
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(start)
            }
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(14.dp).background(Red, androidx.compose.foundation.shape.CircleShape))
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Black).statusBarsPadding().padding(16.dp)) {
                Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pick_on_map_hint), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Black).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(
                    stringResource(R.string.save_place),
                    onClick = { mapView.mapCenter.let { onPick(it.latitude, it.longitude) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
