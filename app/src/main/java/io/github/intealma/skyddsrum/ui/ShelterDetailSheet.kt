package io.github.intealma.skyddsrum.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.Shelter
import io.github.intealma.skyddsrum.data.ShelterWithDistance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterDetailSheet(
    item: ShelterWithDistance,
    onShowOnMap: (Shelter) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val s = item.shelter
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        ) {
            Text(s.address, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.shelter_number, s.id),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            DetailRow(stringResource(R.string.distance),
                if (item.meters.isNaN()) stringResource(R.string.distance_unknown) else Geo.formatDistance(item.meters))
            DetailRow(stringResource(R.string.capacity), pluralStringResource(R.plurals.capacity_people, s.capacity, s.capacity))
            DetailRow(stringResource(R.string.municipality), s.municipality)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openDirections(context, s) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.directions))
                }
                OutlinedButton(onClick = { onShowOnMap(s) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.show_on_map))
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.shelter_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
