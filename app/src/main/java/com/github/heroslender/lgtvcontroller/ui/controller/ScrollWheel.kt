package com.github.heroslender.lgtvcontroller.ui.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The scroll wheel of a TV remote: a drum seen edge-on that rolls up and down without end. Same
 * look as the winder of Expressive Clock (graduations bunch towards the rim, the one under the
 * middle is the marker) with a haptic tick each time a graduation passes. Every pixel dragged is
 * reported through [onScroll] (positive: scroll the TV down).
 */
@Composable
fun ScrollWheel(
    onScroll: (Double) -> Unit,
    modifier: Modifier = Modifier,
    stepRadians: Float = 0.09f,
    majorEvery: Int = 5,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnScroll by rememberUpdatedState(onScroll)
    val accent = MaterialTheme.colorScheme.primary
    val ridge = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    // Position of the drum in graduations; unbounded, so it never reaches an end
    var pos by remember { mutableFloatStateOf(0f) }
    var lastUnit by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Scroll wheel" }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    // Finger up turns the near face up, like the real wheel
                    val radius = size.height / 2f
                    pos += -dy / (radius * stepRadians)

                    val unit = floor(pos)
                    if (unit != lastUnit) {
                        lastUnit = unit
                        haptics.performHapticFeedback(
                            if (Math.floorMod(unit.toInt(), majorEvery) == 0) {
                                HapticFeedbackType.SegmentTick
                            } else {
                                HapticFeedbackType.SegmentFrequentTick
                            }
                        )
                    }

                    // Keep the numbers small without changing what's drawn (100000 is a multiple
                    // of every `majorEvery` we use)
                    if (abs(pos) > 100_000f) {
                        pos -= 100_000f * (if (pos > 0) 1f else -1f)
                        lastUnit -= 100_000f * (if (lastUnit > 0) 1f else -1f)
                    }

                    currentOnScroll(-dy.toDouble() * ScrollWheelSensitivity)
                }
            }
            .fillMaxSize(),
    ) {
        drawRect(track)

        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.height / 2f
        val ridgeLen = size.width * 0.62f
        val halfTurn = (PI / 2 / stepRadians).toInt()
        val selectRange = stepRadians * 1.6f
        val base = floor(pos).toInt()

        for (i in -halfTurn..halfTurn + 1) {
            val unit = base + i
            val theta = (unit - pos) * stepRadians
            val depth = cos(theta)
            if (depth <= 0.02f) continue

            val y = cy + r * sin(theta)
            val major = Math.floorMod(unit, majorEvery) == 0

            val t = (1f - (abs(theta) / selectRange)).coerceIn(0f, 1f)
            val sel = t * t * (3f - 2f * t)

            val alpha = ((if (major) 0.34f else 0.14f) + 0.66f * depth * depth).coerceIn(0f, 1f)
            val len = ridgeLen * (1f + 0.22f * sel)
            val thickness = 6.4f * (0.26f + 0.74f * depth) * (1f + 1.05f * sel)
            val color = lerp(
                (if (major) accent else ridge).copy(alpha = alpha),
                accent,
                sel,
            )

            drawLine(
                color = color,
                start = Offset(cx - len / 2f, y),
                end = Offset(cx + len / 2f, y),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** How far the TV scrolls per pixel dragged on the wheel. */
private const val ScrollWheelSensitivity = 0.6
