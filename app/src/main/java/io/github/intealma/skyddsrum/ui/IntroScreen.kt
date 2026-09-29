package io.github.intealma.skyddsrum.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.intealma.skyddsrum.ui.theme.Black
import io.github.intealma.skyddsrum.ui.theme.White
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Same ASCII intro as the website: the art dissolves left to right, then fades out. Tap to skip. */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val art = remember { AsciiDissolve(context.assets.open("intro_ascii.txt").bufferedReader().use { it.readText() }) }
    var progress by remember { mutableFloatStateOf(0f) }
    val fade = remember { Animatable(1f) }
    val finish by rememberUpdatedState(onFinished)
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(skipped) {
        if (!skipped) {
            val start = withFrameMillis { it } + HOLD_MS
            while (true) {
                val t = withFrameMillis { it } - start
                if (t < 0) continue // show the full art first
                if (t >= DISSOLVE_MS) break
                progress = (0.02 + easeInOutSine(t / DISSOLVE_MS.toDouble()) * 1.03).toFloat()
            }
            progress = 1.05f
        }
        fade.animateTo(0f, tween(FADE_MS))
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Black)
            .alpha(fade.value)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true },
        contentAlignment = Alignment.Center,
    ) {
        // Monospace glyphs are ~0.6em wide; fit the widest line to 92% of the screen width.
        val fontSize = (maxWidth.value * 0.92f / (art.width * 0.6f)).sp
        Text(
            text = art.render(progress),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize, lineHeight = 1.15.em, color = White),
            softWrap = false,
        )
    }
}

private const val HOLD_MS = 700L
private const val DISSOLVE_MS = 2600L
private const val FADE_MS = 450

private fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2

private class AsciiDissolve(raw: String) {
    private val lines = raw.lines()
    val width = lines.maxOf { it.length }
    private val rows = lines.map { it.padEnd(width, ' ') }
    private val erode = charArrayOf('.', ':', '·', '\'', ',')

    // Per-character dissolve threshold: a left-to-right "wind" plus a little noise (same formula as the web).
    private val thresholds = rows.mapIndexed { r, row ->
        DoubleArray(width) { c ->
            if (row[c] == ' ') -1.0
            else {
                val wind = c.toDouble() / width + r.toDouble() / rows.size * 0.25
                val jitter = abs((sin(r * 12.9898 + c * 78.233) * 43758.5453) % 1.0) * 0.35
                wind * 0.75 + jitter
            }
        }
    }

    fun render(progress: Float): String {
        val sb = StringBuilder(rows.size * (width + 1))
        for (r in rows.indices) {
            val row = rows[r]
            val th = thresholds[r]
            for (c in 0 until width) {
                val d = th[c] - progress
                sb.append(
                    when {
                        th[c] < 0 || progress >= 1.05f || d < 0 -> ' '
                        d < 0.05 -> erode[((r * width + c + floor(progress * 97).toInt()) % erode.size)]
                        else -> row[c]
                    },
                )
            }
            if (r < rows.size - 1) sb.append('\n')
        }
        return sb.toString()
    }
}
