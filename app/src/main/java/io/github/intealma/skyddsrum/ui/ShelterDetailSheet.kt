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
import io.github.intealma.skyddsrum.data.TransitStep
import io.github.intealma.skyddsrum.data.ActivityEvent
import io.github.intealma.skyddsrum.data.ShelterStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.intealma.skyddsrum.ui.map.modeLabel
import io.github.intealma.skyddsrum.ui.theme.Red
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt
import io.github.intealma.skyddsrum.ui.theme.Line
import io.github.intealma.skyddsrum.ui.theme.Panel
import io.github.intealma.skyddsrum.ui.theme.PanelRaised
import io.github.intealma.skyddsrum.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelterDetailSheet(
    selection: Selection,
    liveAvailable: Boolean,
    status: ShelterStatus?,
    activity: List<ActivityEvent>?,
    liveConfigured: Boolean,
    onShowOnMap: (Shelter) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val item = selection.item
    val route = selection.route
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
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        ) {
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
                val meters = route?.distanceKm?.times(1000) ?: item.meters
                Stat(stringResource(R.string.distance), if (meters.isNaN()) "–" else Geo.formatDistance(meters), Modifier.weight(1f))
                if (route?.durationMin != null && selection.mode != null) {
                    Stat(
                        stringResource(modeLabel(selection.mode)),
                        stringResource(R.string.minutes, max(1, route.durationMin.roundToInt())),
                        Modifier.weight(1f),
                    )
                }
            }
            route?.steps?.takeIf { it.isNotEmpty() }?.let { steps ->
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.route_steps))
                Spacer(Modifier.height(4.dp))
                steps.forEachIndexed { i, step ->
                    TransitStepRow(step)
                    if (i < steps.lastIndex) HorizontalDivider(color = Line)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.directions), onClick = { openDirections(context, s) }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.show_on_map), onClick = { onShowOnMap(s) }, modifier = Modifier.weight(1f))
            }
            if (liveConfigured) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(14.dp))
                LiveDashboard(liveAvailable, status, s.capacity, activity)
            }
        }
    }
}

@Composable
private fun TransitStepRow(step: TransitStep) {
    val (badge, label, detail) = when (step) {
        is TransitStep.Walk -> Triple(R.string.step_walk, stringResource(R.string.step_walk_to, step.to), times(step.dep, step.arr))
        is TransitStep.Transfer -> Triple(R.string.step_switch, stringResource(R.string.step_change_at, step.at), times(step.dep, step.arr))
        is TransitStep.Ride -> Triple(
            R.string.step_ride,
            if (step.towards != null) stringResource(R.string.step_towards, step.line, step.towards) else step.line,
            "${step.from} ${step.dep} → ${step.to} ${step.arr}",
        )
    }
    val ride = step is TransitStep.Ride
    val shape = RoundedCornerShape(4.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Box(
            Modifier.width(44.dp).height(22.dp).clip(shape)
                .background(if (ride) Red else BadgeGrey)
                .border(1.dp, if (ride) Red else Line, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(badge).uppercase(), style = MaterialTheme.typography.labelSmall, color = if (ride) Color.White else TextMuted)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

private fun times(dep: String, arr: String) = if (dep.isNotBlank() && arr.isNotBlank()) "$dep → $arr" else ""

private val BadgeGrey = Color(0xFF1C1F27)

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
