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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.mimic.R
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
        rememberInfiniteTransition(label = "Plasma ribbon").animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5400, easing = androidx.compose.animation.core.LinearEasing)),
            label = "Plasma phase",
        )
    } else null
    return drawWithCache {
        val center = Offset(size.width / 2f, size.height / 2f)
        val ribbon = Path()
        val brush = Brush.sweepGradient(
            listOf(PlasmaCyan, PlasmaViolet, PlasmaPink, PlasmaCyan, PlasmaViolet, PlasmaPink, PlasmaCyan),
            center = center,
        )
        onDrawBehind {
            val phase = motion?.value ?: 0f
            val unit = size.minDimension
            if (unit <= 0f) return@onDrawBehind
            for (strand in 0..2) {
                ribbon.reset()
                for (i in 0 until 84) {
                    val angle = (i * 2 * PI / 84).toFloat()
                    val wave = sin(3f * angle + phase + strand * .63f) * .027f +
                        cos(5f * angle - phase + strand * .85f) * .014f
                    val radius = unit * (.524f + strand * .012f + wave)
                    val x = center.x + cos(angle) * radius
                    val y = center.y + sin(angle) * radius
                    if (i == 0) ribbon.moveTo(x, y) else ribbon.lineTo(x, y)
                }
                ribbon.close()
                drawPath(ribbon, brush, alpha = .17f, style = Stroke(unit * .058f))
                drawPath(ribbon, brush, alpha = .36f, style = Stroke(unit * .023f))
                drawPath(ribbon, brush, alpha = if (strand == 1) .9f else .56f,
                    style = Stroke(unit * if (strand == 1) .009f else .004f))
            }
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
        Image(painterResource(R.drawable.plasma_nebula), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.matchParentSize()) {
            if (size.minDimension <= 0f) return@Canvas
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
