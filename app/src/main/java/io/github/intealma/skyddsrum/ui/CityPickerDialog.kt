package io.github.intealma.skyddsrum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.GeoPlace

/** Fallback when location is denied or unavailable: pick a municipality from the shelter data. */
@Composable
fun CityPickerDialog(
    municipalities: List<GeoPlace>,
    onPick: (GeoPlace) -> Unit,
    onUseMyLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, municipalities) {
        municipalities.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_city)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_city)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onUseMyLocation) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text(stringResource(R.string.use_my_location), modifier = Modifier.padding(start = 8.dp))
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.name }) { place ->
                        Text(
                            place.name,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(place) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
