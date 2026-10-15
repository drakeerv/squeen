package com.drakeerv.squeen.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Illustrated, animated laundry machine.
 *
 *  - **Washers** fill with sloshing water, bubble, and their load climbs the drum wall
 *    before tumbling back down into the water.
 *  - **Dryers** tumble the load faster while warm heat waves drift up through the drum.
 *
 * Everything is drawn on a single [Canvas] in the draw phase (no recomposition churn) and
 * scales with the given size; small sizes automatically simplify the artwork so the icon
 * stays crisp in grids.
 *
 * @param tint      bezel, drum fins, water and heat colour
 * @param load      laundry / bubble colour
 * @param glass     drum interior colour
 */
@Composable
fun MachineAnimation(
    isDryer: Boolean,
    running: Boolean,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    load: Color? = null,
    glass: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // One shared look for every machine illustration, in both themes: themed bezel,
    // a drum that contrasts with its container, and neutral laundry.
    val resolvedTint = tint ?: if (isDryer) scheme.tertiary else scheme.primary
    val resolvedLoad = load ?: scheme.onSurface
    val resolvedGlass = glass ?: scheme.surfaceContainerLowest
    val transition = rememberInfiniteTransition(label = "machine")

    // Drum rotation / tumbling cycle (one revolution per loop).
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isDryer) 2600 else 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )
    // Softer secondary motion for water slosh / heat pulse / bubbles.
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Canvas(modifier = modifier) {
        val cyclePhase = if (running) cycle else 0f
        val breath = if (running) breathe else 0.35f
        if (isDryer) {
            drawDryerDrum(resolvedTint, resolvedLoad, resolvedGlass, cyclePhase, breath, running)
        } else {
            drawWasherDrum(resolvedTint, resolvedLoad, resolvedGlass, cyclePhase, breath, running)
        }
    }
}

// --------------------------------------------------------------- drum shell

private fun DrawScope.drumShell(tint: Color, glass: Color, load: Color, center: Offset, radius: Float, rim: Float) {
    val innerRadius = radius - rim

    // Outer bezel with a metallic sweep.
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                tint.copy(alpha = 0.95f),
                tint.copy(alpha = 0.55f),
                tint.copy(alpha = 0.95f),
                tint.copy(alpha = 0.65f),
                tint.copy(alpha = 0.95f),
            ),
            center = center,
        ),
        radius = radius - rim / 2f,
        center = center,
        style = Stroke(width = rim),
    )

    // Drum interior: a glass well with depth.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glass.copy(alpha = 0.92f).blendWith(load, 0.10f),
                glass.copy(alpha = 0.98f),
                glass.copy(alpha = 0.85f).blendWith(Color.Black, 0.35f),
            ),
            center = Offset(center.x - innerRadius * 0.35f, center.y - innerRadius * 0.4f),
            radius = innerRadius * 1.5f,
        ),
        radius = innerRadius,
        center = center,
    )

    // Inner rim ring for depth.
    drawCircle(
        color = tint.copy(alpha = 0.35f),
        radius = innerRadius - rim * 0.15f,
        center = center,
        style = Stroke(width = rim * 0.22f),
    )
}

/** Three drum paddles that rotate with the drum. */
private fun DrawScope.drumFins(tint: Color, center: Offset, innerRadius: Float, angle: Float, rim: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    repeat(3) { index ->
        rotate(degrees = angle + index * 120f, pivot = center) {
            val finLength = innerRadius * 0.42f
            val finWidth = (rim * 0.55f).coerceAtLeast(innerRadius * 0.10f)
            drawRoundRect(
                color = tint.copy(alpha = 0.30f * alpha),
                topLeft = Offset(center.x - finWidth / 2f, center.y - innerRadius + rim * 0.35f),
                size = Size(finWidth, finLength),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(finWidth / 2f, finWidth / 2f),
            )
        }
    }
}

private fun DrawScope.glassHighlight(center: Offset, innerRadius: Float) {
    // Specular arc on the upper-left of the glass.
    drawArc(
        color = Color.White.copy(alpha = 0.16f),
        startAngle = 205f,
        sweepAngle = 78f,
        useCenter = false,
        topLeft = Offset(center.x - innerRadius * 0.78f, center.y - innerRadius * 0.78f),
        size = Size(innerRadius * 1.56f, innerRadius * 1.56f),
        style = Stroke(width = innerRadius * 0.10f, cap = StrokeCap.Round),
    )
    // Soft vignette to seat the glass in the bezel.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)),
            center = center,
            radius = innerRadius,
        ),
        radius = innerRadius,
        center = center,
        alpha = 0.9f,
    )
}

// ------------------------------------------------------------------ washers

private fun DrawScope.drawWasherDrum(
    tint: Color,
    load: Color,
    glass: Color,
    cycle: Float,
    breath: Float,
    running: Boolean,
) {
    val radius = size.minDimension / 2f
    val center = this.center
    val rim = radius * 0.16f
    val innerRadius = radius - rim
    val compact = size.minDimension < 64.dp.toPx()

    drumShell(tint, glass, load, center, radius, rim)

    val glassClip = circlePath(center, innerRadius)
    clipPath(glassClip) {
        drumFins(tint, center, innerRadius, angle = cycle * 360f, rim = rim, alpha = 1f)

        if (!running) {
            // An idle machine is an empty drum: no water, no laundry, no bubbles.
            return@clipPath
        }

        // ---- water body
        val waterLevel = center.y + innerRadius * (0.10f + 0.06f * sin(breath * 2f * PI.toFloat()))
        val waterPath = Path().apply {
            moveTo(center.x - innerRadius, waterLevel)
            waveTo(center.x - innerRadius, center.x + innerRadius, waterLevel, innerRadius * 0.10f, breath * 2f * PI.toFloat())
            lineTo(center.x + innerRadius, center.y + innerRadius)
            lineTo(center.x - innerRadius, center.y + innerRadius)
            close()
        }
        drawPath(
            path = waterPath,
            brush = Brush.verticalGradient(
                colors = listOf(tint.copy(alpha = 0.40f), tint.copy(alpha = 0.68f)),
                startY = waterLevel,
                endY = center.y + innerRadius,
            ),
        )
        // surface sheen
        drawPath(
            path = Path().apply {
                moveTo(center.x - innerRadius, waterLevel)
                waveTo(center.x - innerRadius, center.x + innerRadius, waterLevel, innerRadius * 0.10f, breath * 2f * PI.toFloat())
            },
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = rim * 0.16f, cap = StrokeCap.Round),
        )

        // ---- tumbling load: slow, heavy, falling into the water
        drawTumbleItems(
            count = if (compact) 3 else 4,
            cycle = cycle,
            center = center,
            innerRadius = innerRadius,
            tint = tint,
            load = load,
            speed = 1f,
            bounce = 0.15f,
            rideFraction = 0.58f,
        )
        // splash ripples where the load lands
        repeat(2) { index ->
            val ripple = fract(cycle * 2f + index * 0.5f)
            val rippleY = center.y + innerRadius * 0.16f
            if (!compact || index == 0) {
                drawOval(
                    color = Color.White.copy(alpha = (1f - ripple) * 0.20f),
                    topLeft = Offset(center.x - innerRadius * (0.15f + ripple * 0.5f), rippleY),
                    size = Size(innerRadius * (0.3f + ripple), innerRadius * 0.07f),
                )
            }
        }

        // ---- bubbles rising through the water
        if (!compact) {
            repeat(4) { index ->
                val bp = fract(cycle * 1.6f + index * 0.27f)
                val bubbleX = center.x + innerRadius * (0.5f - index * 0.32f)
                val bubbleY = center.y + innerRadius * (0.75f - bp * 1.1f)
                val alpha = ((1f - bp) * 0.5f).coerceIn(0f, 0.5f)
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = innerRadius * (0.035f + 0.02f * (index % 2)),
                    center = Offset(bubbleX, bubbleY),
                )
            }
        }
    }

    glassHighlight(center, innerRadius)
}

// ------------------------------------------------------------------- dryers

private fun DrawScope.drawDryerDrum(
    tint: Color,
    load: Color,
    glass: Color,
    cycle: Float,
    breath: Float,
    running: Boolean,
) {
    val radius = size.minDimension / 2f
    val center = this.center
    val rim = radius * 0.16f
    val innerRadius = radius - rim
    val compact = size.minDimension < 64.dp.toPx()

    drumShell(tint, glass, load, center, radius, rim)

    clipPath(circlePath(center, innerRadius)) {
        drumFins(tint, center, innerRadius, angle = -cycle * 360f, rim = rim, alpha = 1f)

        if (!running) {
            // An idle dryer is an empty drum: no heat, no laundry.
            return@clipPath
        }

        // warm glow building up at the bottom of the drum
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.30f + 0.18f * breath), Color.Transparent),
                center = Offset(center.x, center.y + innerRadius * 0.65f),
                radius = innerRadius * 1.1f,
            ),
            radius = innerRadius,
            center = center,
        )

        // ---- tumbling load: light, airy, tumbling twice per revolution
        drawTumbleItems(
            count = if (compact) 3 else 4,
            cycle = cycle,
            center = center,
            innerRadius = innerRadius,
            tint = tint,
            load = load,
            speed = 2f, // whole multiple of the drum loop => the tumble wraps without jumping
            bounce = 0.8f,
            rideFraction = 0.5f,
        )

        // ---- heat waves drifting up through the drum
        val waves = if (compact) 2 else 3
        repeat(waves) { index ->
            val wp = fract(cycle * 0.9f + index * 0.31f)
            val y = center.y + innerRadius * (0.85f - wp * 1.5f)
            val alpha = (sin(wp * PI.toFloat()) * 0.55f).coerceIn(0f, 0.55f)
            val amplitude = innerRadius * 0.13f * (0.55f + 0.45f * breath)
            val width = innerRadius * (1.15f - index * 0.12f)
            drawPath(
                path = heatWavePath(center.x, y, width, amplitude, index),
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        tint.copy(alpha = alpha),
                        Color.Transparent,
                    ),
                ),
                style = Stroke(width = rim * (if (compact) 0.16f else 0.13f), cap = StrokeCap.Round),
            )
        }
    }

    glassHighlight(center, innerRadius)
}

// ------------------------------------------------------------ small helpers

/**
 * Draws the tumbling load with a seamless ride-and-fall loop:
 * every item climbs the drum wall from the bottom to the top on a fixed lateral offset,
 * falls straight back down to exactly where it started (with an optional bounce for dryers),
 * so the loop never jumps.
 */
private fun DrawScope.drawTumbleItems(
    count: Int,
    cycle: Float,
    center: Offset,
    innerRadius: Float,
    tint: Color,
    load: Color,
    speed: Float,
    bounce: Float,
    rideFraction: Float = 0.55f,
) {
    repeat(count) { index ->
        val offset = index / count.toFloat()
        val t = fract(cycle * speed + offset)
        val lateral = (index - (count - 1) / 2f) * innerRadius * 0.24f
        val orbit = innerRadius * 0.56f
        val bottomAngle = 90.0
        val topAngle = 270.0
        val itemRadius = innerRadius * (0.13f + 0.035f * (index % 3))

        val position = if (t < rideFraction) {
            val climb = easeInOut(t / rideFraction)
            val angle = Math.toRadians(bottomAngle + (topAngle - bottomAngle) * climb)
            Offset(
                center.x + lateral + cos(angle).toFloat() * orbit,
                center.y + sin(angle).toFloat() * orbit,
            )
        } else {
            val fall = (t - rideFraction) / (1f - rideFraction)
            val eased = fall * fall
            val topX = center.x + lateral
            val topY = center.y - orbit
            val bottomY = center.y + orbit
            val rebound = if (bounce > 0f && fall > 0.7f) {
                -sin((fall - 0.7f) / 0.3f * PI.toFloat()) * orbit * 0.22f * bounce
            } else {
                0f
            }
            Offset(
                topX,
                topY + (bottomY - topY) * eased + rebound,
            )
        }
        drawTumbleItem(position, itemRadius, tint, load, index)
    }
}

/** A soft, cloth-like tumbling item (rounded capsule with a highlight). */
private fun DrawScope.drawTumbleItem(center: Offset, radius: Float, tint: Color, load: Color, seed: Int) {
    val w = radius * 1.7f
    val h = radius * 1.25f
    rotate(degrees = (seed * 37f) + center.x % 11f, pivot = center) {
        drawRoundRect(
            color = load.copy(alpha = 0.85f),
            topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.55f, radius * 0.55f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(center.x - w / 2f + w * 0.14f, center.y - h / 2f + h * 0.14f),
            size = Size(w * 0.34f, h * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.11f, h * 0.11f),
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.20f),
            topLeft = Offset(center.x - w / 2f + w * 0.10f, center.y + h * 0.12f),
            size = Size(w * 0.8f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.12f, h * 0.12f),
        )
    }
}

private fun circlePath(center: Offset, radius: Float): Path = Path().apply {
    addOval(
        Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius,
        )
    )
}

/** Appends a gentle two-bump wave along the x axis at [y] with displacement [amplitude]. */
private fun Path.waveTo(x0: Float, x1: Float, y: Float, amplitude: Float, phase: Float) {
    val width = x1 - x0
    val segments = 3
    for (i in 0 until segments) {
        val fromX = x0 + width * (i / segments.toFloat())
        val toX = x0 + width * ((i + 1) / segments.toFloat())
        val direction = if (i % 2 == 0) -1f else 1f
        val lift = amplitude * direction * sin(phase + i * 1.2f)
        quadraticTo((fromX + toX) / 2f, y + lift, toX, y)
    }
}

/** Heat wave: smooth rising S-curve. */
private fun heatWavePath(centerX: Float, y: Float, width: Float, amplitude: Float, seed: Int): Path = Path().apply {
    val half = width / 2f
    val dir = if (seed % 2 == 0) 1f else -1f
    moveTo(centerX - half, y)
    cubicTo(
        centerX - half * 0.5f, y - amplitude * dir,
        centerX - half * 0.5f, y - amplitude * dir,
        centerX, y,
    )
    cubicTo(
        centerX + half * 0.5f, y + amplitude * dir,
        centerX + half * 0.5f, y + amplitude * dir,
        centerX + half, y,
    )
}

private fun fract(value: Float): Float = value - kotlin.math.floor(value.toDouble()).toFloat()

private fun easeInOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun Color.blendWith(other: Color, ratio: Float): Color = Color(
    red = red * (1f - ratio) + other.red * ratio,
    green = green * (1f - ratio) + other.green * ratio,
    blue = blue * (1f - ratio) + other.blue * ratio,
    alpha = alpha * (1f - ratio) + other.alpha * ratio,
)
