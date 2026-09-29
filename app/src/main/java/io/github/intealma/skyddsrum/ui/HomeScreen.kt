package io.github.intealma.skyddsrum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted

@Composable
fun HomeScreen(
    shelterCount: Int?,
    origin: Origin?,
    locating: Boolean,
    message: Int?,
    nearest: List<ShelterWithDistance>,
    onFindNearest: () -> Unit,
    onChangeOrigin: () -> Unit,
    onOpen: (ShelterWithDistance) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            BrandHeader(
                subtitle = if (shelterCount == null) stringResource(R.string.loading_shelters)
                else stringResource(R.string.home_tagline, shelterCount),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (locating) stringResource(R.string.locating) else stringResource(R.string.find_nearest),
                onClick = onFindNearest,
                enabled = shelterCount != null && !locating,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.nearest_title), Modifier.weight(1f))
                Text(
                    stringResource(R.string.change).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.clickable(onClick = onChangeOrigin).padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
            if (origin != null) {
                Text(
                    stringResource(
                        R.string.from_origin,
                        if (origin.isDeviceLocation) stringResource(R.string.your_location) else origin.place.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        if (nearest.isEmpty()) {
            item {
                Text(stringResource(R.string.nearest_empty), style = MaterialTheme.typography.bodySmall, color = TextFaint)
            }
        }
        itemsIndexed(nearest, key = { _, it -> it.shelter.id }) { index, item ->
            ShelterRow(index + 1, item, onClick = { onOpen(item) })
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.emergency_info), style = MaterialTheme.typography.bodySmall, color = TextFaint)
        }
    }
}

@Composable
fun BrandHeader(subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShelterMark(28.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.2.sp),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
