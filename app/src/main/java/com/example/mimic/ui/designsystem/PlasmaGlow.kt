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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import com.example.mimic.R
import kotlin.math.atan2
import kotlin.math.sqrt
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

private const val HaloMeshSteps = 32

@Composable
internal fun Modifier.plasmaArtworkHalo(animate: Boolean): Modifier {
    val cyan = ImageBitmap.imageResource(R.drawable.plasma_halo_cyan).asAndroidBitmap()
    val violet = ImageBitmap.imageResource(R.drawable.plasma_halo_violet).asAndroidBitmap()
    val magenta = ImageBitmap.imageResource(R.drawable.plasma_halo_magenta).asAndroidBitmap()
    val motion = if (animate) {
        rememberInfiniteTransition(label = "Plasma waves").animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)),
            label = "Plasma wave phase",
        )
    } else null
    return drawWithCache {
        val layers = listOf(violet, magenta, cyan)
        val side = HaloMeshSteps + 1
        val count = side * side
        val xs = FloatArray(count) { (it % side).toFloat() / HaloMeshSteps - .5f }
        val ys = FloatArray(count) { (it / side).toFloat() / HaloMeshSteps - .5f }
        val angles = FloatArray(count) { atan2(ys[it], xs[it]) }
        val envelope = FloatArray(count) {
            val radius = sqrt(xs[it] * xs[it] + ys[it] * ys[it])
            ((radius - .19f) / .16f).coerceIn(0f, 1f)
        }
        val vertices = FloatArray(2 * count)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        val speeds = intArrayOf(2, -3, 1)
        val diameter = size.minDimension * 1.60f
        val origin = Offset(size.width / 2f, size.height / 2f)
        onDrawBehind {
            val phase = motion?.value ?: 0f
            drawIntoCanvas { canvas ->
                for (layer in layers.indices) {
                    val time = phase * speeds[layer]
                    for (i in 0 until count) {
                        val angle = angles[i]
                        val wave = sin((3 + layer) * angle + time)
                        val ripple = sin((7 - layer) * angle - time)
                        val radial = 1f + envelope[i] * (.11f * wave + .045f * ripple)
                        val shear = envelope[i] * .022f * sin(4 * angle - time)
                        vertices[2 * i] = origin.x + diameter * (xs[i] * radial - ys[i] * shear)
                        vertices[2 * i + 1] = origin.y + diameter * (ys[i] * radial + xs[i] * shear)
                    }
                    canvas.nativeCanvas.drawBitmapMesh(
                        layers[layer], HaloMeshSteps, HaloMeshSteps, vertices, 0, null, 0, paint,
                    )
                }
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
