package io.github.intealma.skyddsrum.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.ui.theme.Line
import io.github.intealma.skyddsrum.ui.theme.Panel
import io.github.intealma.skyddsrum.ui.theme.PanelRaised
import io.github.intealma.skyddsrum.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterDetailSheet(
    item: ShelterWithDistance,
    onShowOnMap: (Shelter) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val s = item.shelter
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PanelRaised,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 14.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(3.dp)).background(Line))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row {
                ShelterMark(36.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.address, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${s.municipality} · ${stringResource(R.string.shelter_number, s.id)}".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stringResource(R.string.capacity), pluralStringResource(R.plurals.capacity_people, s.capacity, s.capacity), Modifier.weight(1f))
                Stat(
                    stringResource(R.string.distance),
                    if (item.meters.isNaN()) "–" else Geo.formatDistance(item.meters),
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.directions), onClick = { openDirections(context, s) }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.show_on_map), onClick = { onShowOnMap(s) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Column(modifier.clip(shape).background(Panel).border(1.dp, Line, shape).padding(horizontal = 12.dp, vertical = 10.dp)) {
        SectionLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Opens the user's maps app via a geo: intent; falls back to OpenStreetMap in the browser. */
fun openDirections(context: Context, shelter: Shelter) {
    val label = Uri.encode(shelter.address)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${shelter.lat},${shelter.lon}?q=${shelter.lat},${shelter.lon}($label)"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.openstreetmap.org/directions?to=${shelter.lat}%2C${shelter.lon}#map=17/${shelter.lat}/${shelter.lon}"),
    )
    for (intent in listOf(geo, web)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // try the next option
        }
    }
    Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_SHORT).show()
}
