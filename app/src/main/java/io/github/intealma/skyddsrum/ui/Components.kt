package io.github.intealma.skyddsrum.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.intealma.skyddsrum.R
import io.github.intealma.skyddsrum.data.Geo
import io.github.intealma.skyddsrum.data.LiveMath
import io.github.intealma.skyddsrum.data.ShelterStatus
import io.github.intealma.skyddsrum.data.ShelterWithDistance
import io.github.intealma.skyddsrum.ui.theme.Black
import io.github.intealma.skyddsrum.ui.theme.Line
import io.github.intealma.skyddsrum.ui.theme.LineStrong
import io.github.intealma.skyddsrum.ui.theme.PanelRaised
import io.github.intealma.skyddsrum.ui.theme.Red
import io.github.intealma.skyddsrum.ui.theme.TextFaint
import io.github.intealma.skyddsrum.ui.theme.TextMuted
import io.github.intealma.skyddsrum.ui.theme.White

private val PanelShape = RoundedCornerShape(6.dp)
private val ButtonShape = RoundedCornerShape(4.dp)

/** Outline square with a triangle: the shelter mark used on the website. */
@Composable
fun ShelterMark(size: Dp = 28.dp, color: Color = White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.055f
        drawRect(color, topLeft = Offset(s * 0.125f, s * 0.125f), size = Size(s * 0.75f, s * 0.75f), style = Stroke(stroke))
        val tri = Path().apply {
            moveTo(s * 0.5f, s * 0.3f)
            lineTo(s * 0.75f, s * 0.675f)
            lineTo(s * 0.25f, s * 0.675f)
            close()
        }
        drawPath(tri, color, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/** Raised panel with a 1dp border, like the website's cards. */
fun Modifier.panel(): Modifier = this
    .clip(PanelShape)
    .background(PanelRaised)
    .border(1.dp, Line, PanelShape)

/** Small uppercase label in faint grey. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = modifier)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(height),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    OutlinedButton(
        onClick = onClick,
        shape = ButtonShape,
        border = BorderStroke(1.dp, LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(height),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.labelLarge) }
}

/** One row in a nearest-shelters list: red rank dot, address, distance. */
@Composable
fun ShelterRow(rank: Int, item: ShelterWithDistance, onClick: () -> Unit, modifier: Modifier = Modifier, status: ShelterStatus? = null) {
    Row(
        modifier.fillMaxWidth().panel().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
            Text("$rank", style = MaterialTheme.typography.labelMedium, color = White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.shelter.address, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Row {
                Text(
                    "${Geo.formatDistance(item.meters)} · " +
                        pluralStringResource(R.plurals.capacity_people, item.shelter.capacity, item.shelter.capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                occupancyText(status, item.shelter.capacity)?.let {
                    Text(
                        " · $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (LiveMath.isFull(status, item.shelter.capacity)) LevelRed else LevelGreen,
                    )
                }
            }
        }
    }
}
