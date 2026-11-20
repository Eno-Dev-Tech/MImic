package com.example.mimic.ui.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal val LocalPlasmaGlow = staticCompositionLocalOf { false }

internal fun Modifier.plasmaArtworkFrame(shape: Shape): Modifier =
    dropShadow(shape, Shadow(radius = 25.dp, color = Color(0x886C3DF9), spread = 3.dp))
        .dropShadow(shape, Shadow(radius = 9.dp, color = Color(0x66E044BB)))
        .border(1.dp, Brush.linearGradient(listOf(Color(0xFF9C83FF), Color(0xFF69D9FA), Color(0xFFDD6DDE))), shape)

private val PlasmaCyan = Color(0xFF80E8FF)
private val PlasmaViolet = Color(0xFF997CFF)
private val PlasmaPink = Color(0xFFF092E4)

internal fun Modifier.plasmaSurface(shape: Shape, active: Boolean = false, colored: Boolean = true): Modifier {
    val edgeAlpha = if (active) .82f else .37f
    return dropShadow(shape, Shadow(radius = if (active) 12.dp else 6.dp,
        color = if (colored) PlasmaViolet.copy(alpha = if (active) .42f else .20f) else Color.Black.copy(alpha = .46f)))
        .clip(shape)
        .background(Brush.verticalGradient(
            listOf(Color(0xFF303044), Color(0xFF1A1927), Color(0xFF11111B)),
        ))
        .border(1.dp, if (!colored) androidx.compose.ui.graphics.SolidColor(Color(0xFF454255)) else Brush.linearGradient(listOf(
            PlasmaCyan.copy(alpha = edgeAlpha), PlasmaViolet.copy(alpha = edgeAlpha * .30f),
            PlasmaPink.copy(alpha = edgeAlpha),
        )), shape)
}

@Composable
internal fun PlasmaButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    state: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.size(size)
            .plasmaSurface(CircleShape, colored = false)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (state != null) stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        AuroraIcon(icon, label, iconSize, if (!enabled) -.5f else if (active || pressed) 1f else .28f)
    }
}

@Composable
internal fun AuroraIcon(icon: ImageVector, label: String?, size: Dp, highlight: Float) {
    Box(contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.blur(3.dp)
            .size(size).auroraGlyph((.26f + .52f * highlight).coerceIn(0f, 1f)))
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)
            .auroraGlyph((.72f + .28f * highlight).coerceIn(0f, 1f)))
    }
}

private fun Modifier.auroraGlyph(alpha: Float): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        this.alpha = alpha
    }.drawWithContent {
        drawContent()
        drawRect(
            Brush.linearGradient(listOf(Color(0xFF91F3FF), Color(0xFF83AEFF), Color(0xFFC0A2FF), Color(0xFFFF9BDA))),
            blendMode = BlendMode.SrcIn,
        )
    }

internal fun plasmaProgressBrush(): Brush =
    Brush.horizontalGradient(listOf(PlasmaCyan, PlasmaViolet, PlasmaPink))

@Composable
internal fun Modifier.plasmaArtworkHalo(animate: Boolean): Modifier {
    val motion = if (animate) {
        rememberInfiniteTransition(label = "Plasma waveform").animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4800, easing = LinearEasing)),
            label = "Waveform motion",
        )
    } else null
    return drawWithCache {
        val origin = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension
        val halfWidth = unit * .90f
        val samples = 360
        val positions = FloatArray(samples + 1) { it * 2f / samples - 1f }
        val envelope = FloatArray(samples + 1) {
            val x = positions[it]
            kotlin.math.exp(-3.8f * x * x) * (1f - x * x)
        }
        val waveform = Path()
        val gradient = Brush.horizontalGradient(
            0f to Color(0xFF43F2DF),
            .18f to Color(0xFF62BEFF),
            .34f to Color(0xFFA276EF),
            .49f to Color(0xFFED13D0),
            .62f to Color(0xFFF34C9B),
            .78f to Color(0xFFD7AD68),
            .9f to Color(0xFFDBEF4B),
            1f to Color(0xFFBDF24B),
            startX = origin.x - halfWidth,
            endX = origin.x + halfWidth,
        )
        onDrawBehind {
            val phase = motion?.value ?: 0f
            waveform.reset()
            for (i in positions.indices) {
                val position = positions[i]
                val pulse = 1f + .065f * sin(2f * phase + position * 3f)
                val amplitude = unit * .80f * envelope[i] * pulse
                val oscillation = sin(position * 12f * PI.toFloat() - phase)
                val x = origin.x + position * halfWidth
                val y = origin.y + oscillation * amplitude
                if (i == 0) waveform.moveTo(x, y) else waveform.lineTo(x, y)
            }
            drawPath(waveform, gradient, alpha = .12f,
                style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(waveform, gradient,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun PlasmaGlowBackdrop(modifier: Modifier, animate: Boolean) {
    val drift = if (animate) {
        rememberInfiniteTransition(label = "Plasma glow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Reverse),
            label = "Glow position",
        )
    } else {
        null
    }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            if (size.minDimension <= 0f) return@Canvas
            drawRect(Brush.verticalGradient(listOf(Color(0xFF26093F), Color(0xFF100722), Color(0xFF080610))))
            val phase = drift?.value ?: 0.5f
            drawRect(Brush.radialGradient(
                colors = listOf(Color(0x555F27BE), Color.Transparent),
                center = Offset(size.width * (0.17f + phase * 0.1f), size.height * 0.28f),
                radius = size.width * 0.94f,
            ))
            drawRect(Brush.radialGradient(
                colors = listOf(Color(0x42D83C97), Color.Transparent),
                center = Offset(size.width * 0.93f, size.height * (0.40f + phase * 0.09f)),
                radius = size.width * 0.75f,
            ))
            drawRect(Brush.radialGradient(
                colors = listOf(Color(0x383F85E7), Color.Transparent),
                center = Offset(size.width * (0.38f - phase * 0.09f), size.height * 0.78f),
                radius = size.width * 0.73f,
            ))
        }
    }
}
