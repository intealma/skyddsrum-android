package io.github.intealma.skyddsrum.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.ActivityEvent
import io.github.intealma.skyddsrum.data.LiveMath
import io.github.intealma.skyddsrum.data.LiveSnapshot
import io.github.intealma.skyddsrum.data.ResourceLevel
import io.github.intealma.skyddsrum.data.ShelterStatus
import io.github.intealma.skyddsrum.ui.theme.Red
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted
import io.github.intealma.skyddsrum.ui.theme.White
import kotlin.math.roundToInt

// Colour only for levels, like the website's calm dashboard; everything else stays neutral.
val LevelGreen = Color(0xFF4ADE80)
val LevelOrange = Color(0xFFFFA64D)
val LevelRed = Color(0xFFFF5C5C)
private val Track = Color(0xFF1A1C22)
private val RowLine = Color(0x0DFFFFFF)

/** Small pulsing red dot + "LIVE". */
@Composable
fun LiveLabel(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).alpha(pulse).clip(CircleShape).background(Red))
        Spacer(Modifier.width(6.dp))
        SectionLabel(stringResource(R.string.live))
    }
}

@Composable
fun relativeTime(atMillis: Long, now: Long = System.currentTimeMillis()): String {
    val mins = ((now - atMillis) / 60_000).coerceAtLeast(0).toInt()
    return when {
        mins < 1 -> stringResource(R.string.just_now)
        mins < 60 -> stringResource(R.string.minutes_ago, mins)
        else -> stringResource(R.string.hours_ago, (mins / 60.0).roundToInt())
    }
}

/** "12 inside" / "full" for list rows; null when nobody is checked in. */
@Composable
fun occupancyText(status: ShelterStatus?, capacity: Int): String? = when {
    status == null || status.occupancy <= 0 -> null
    LiveMath.isFull(status, capacity) -> stringResource(R.string.full)
    else -> stringResource(R.string.inside, status.occupancy)
}

/** Home: total people checked in right now and the latest arrivals anywhere. */
@Composable
fun LiveHomeSection(snapshot: LiveSnapshot?, addressOf: (String) -> String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        LiveLabel()
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().panel().padding(14.dp)) {
            when {
                snapshot == null -> Text(stringResource(R.string.live_unavailable), style = MaterialTheme.typography.bodySmall, color = TextFaint)
                snapshot.totalPeople == 0 -> Text(stringResource(R.string.live_empty), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                else -> {
                    Text(
                        stringResource(R.string.live_summary, snapshot.totalPeople, snapshot.activeShelters),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val arrivals = snapshot.recent.take(3)
                    if (arrivals.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SectionLabel(stringResource(R.string.live_arrivals))
                        arrivals.forEach { e -> ArrivalRow(e, addressOf(e.shelterId)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.live_source), style = MaterialTheme.typography.bodySmall, color = TextFaint)
        }
    }
}

@Composable
private fun ArrivalRow(e: ActivityEvent, address: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            address ?: e.shelterId,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.live_arrival, e.people, relativeTime(e.atMillis)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

/** Detail sheet: the website's calm dashboard for one shelter. */
@Composable
fun LiveDashboard(available: Boolean, status: ShelterStatus?, capacity: Int, activity: List<ActivityEvent>?) {
    Column(Modifier.fillMaxWidth()) {
        LiveLabel()
        Spacer(Modifier.height(8.dp))
        if (!available) {
            Text(stringResource(R.string.live_unavailable), style = MaterialTheme.typography.bodySmall, color = TextFaint)
            return@Column
        }
        if (status == null || status.checkinCount == 0) {
            Text(stringResource(R.string.nobody_here), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            return@Column
        }
        OccupancyHero(status.occupancy, capacity)
        Spacer(Modifier.height(18.dp))

        val water = LiveMath.resourceLevel(status.waterNone, status.water1to3, status.water4plus)
        val food = LiveMath.resourceLevel(status.foodNone, status.food1day, status.food2plus)
        DashCard(stringResource(R.string.current_needs)) {
            DashRow(stringResource(R.string.water), levelText(water), levelColor(water))
            DashRow(stringResource(R.string.food), levelText(food), levelColor(food))
            DashRow(
                stringResource(R.string.medical_staff),
                if (status.medical > 0) stringResource(R.string.n_available, status.medical) else stringResource(R.string.none_available),
                if (status.medical > 0) LevelGreen else LevelRed,
            )
        }
        DashCard(stringResource(R.string.people_skills)) {
            DashRow(stringResource(R.string.adults), "${status.adults}")
            DashRow(stringResource(R.string.children), "${status.children}")
            DashRow(stringResource(R.string.skill_medical), "${status.medical}")
            DashRow(stringResource(R.string.skill_leadership), "${status.leadership}")
            DashRow(stringResource(R.string.skill_technical), "${status.technical}")
            DashRow(stringResource(R.string.skill_community), "${status.community}")
            DashRow(stringResource(R.string.skill_volunteers), "${status.volunteer}")
            DashRow(stringResource(R.string.special_needs), "${status.specialNeeds}", if (status.specialNeeds > 0) LevelOrange else null)
        }
        DashCard(stringResource(R.string.resources)) {
            DashRow(stringResource(R.string.water), stringResource(R.string.water_total, status.waterLitres))
            DashRow(stringResource(R.string.food), stringResource(R.string.food_total, status.foodDays))
            DashRow(stringResource(R.string.power_banks), "${status.powerBanks}")
            DashRow(stringResource(R.string.first_aid_kits), "${status.firstAidKits}")
            DashRow(stringResource(R.string.radios), "${status.radios}")
            DashRow(stringResource(R.string.flashlights), "${status.flashlights}")
        }
        val buckets = activity?.let { LiveMath.bucketActivity(it).take(6) }.orEmpty()
        if (buckets.isNotEmpty()) {
            DashCard(stringResource(R.string.recent_activity)) {
                buckets.forEach { b -> DashRow(relativeTime(b.latestMillis), stringResource(R.string.checked_in_n, b.people)) }
            }
        }
    }
}

@Composable
private fun OccupancyHero(occupancy: Int, capacity: Int) {
    val pct = if (capacity > 0) occupancy.toFloat() / capacity else 0f
    val barColor = when {
        pct >= 1f -> LevelRed
        pct >= 0.7f -> LevelOrange
        else -> LevelGreen
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$occupancy", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp), color = White)
            Text(" / ", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp), color = TextFaint)
            Text(if (capacity > 0) "$capacity" else "–", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp), color = White)
        }
        SectionLabel(stringResource(R.string.people_sheltered))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(Track)) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(7.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
        }
        if (capacity > 0) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.percent_full, (pct * 100).roundToInt()), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun DashCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = White)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DashRow(label: String, value: String, valueColor: Color? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor ?: White)
        }
        HorizontalDivider(color = RowLine)
    }
}

@Composable
private fun levelText(level: ResourceLevel) = stringResource(
    when (level) {
        ResourceLevel.NO_DATA -> R.string.no_data
        ResourceLevel.LOW -> R.string.level_low
        ResourceLevel.MODERATE -> R.string.level_moderate
        ResourceLevel.GOOD -> R.string.level_good
    },
)

private fun levelColor(level: ResourceLevel): Color? = when (level) {
    ResourceLevel.NO_DATA -> null
    ResourceLevel.LOW -> LevelRed
    ResourceLevel.MODERATE -> LevelOrange
    ResourceLevel.GOOD -> LevelGreen
}
