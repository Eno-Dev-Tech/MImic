package com.example.mimic.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimic.data.NavStyle
import com.example.mimic.ui.designsystem.ThemeRegistry
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class NavigationMotion {
    SLIDING_PILL, LIQUID_BULGE, FLOATING_NOTCH,
    ICON_FILL, EXPANDING_LABEL, BUBBLE_FLOAT
}

data class MotionTab(val key: String, val label: String, val icon: ImageVector, val filledIcon: ImageVector = icon)

data class MotionPalette(
    val surface: Color, val ink: Color, val accent: Color, val onAccent: Color,
    val outline: Color, val felt: Boolean = false
) {
    companion object {
        val ClassicFelt = MotionPalette(Color(0xFFF2E7D0), Color(0xFF35486B), Color(0xFFA93A2B), Color.White, Color(0xFF827B70), true)
        val OrangeFelt = MotionPalette(Color(0xFF242225), Color(0xFFE6DED3), Color(0xFFFF801F), Color(0xFF20160D), Color(0xFF8E7967), true)
    }
}

/** Destination state is owned by the caller. No new navigation controller is created. */
@Composable
fun AnimatedMimicNavigation(
    tabs: List<MotionTab>, selectedKey: String, onSelect: (String) -> Unit,
    motion: NavigationMotion, modifier: Modifier = Modifier,
    palette: MotionPalette = MotionPalette(MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.outline),
    reduceMotion: Boolean = false
) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
    val target = selectedIndex.coerceAtLeast(0).toFloat()
    val position by animateFloatAsState(target, if (reduceMotion) tween(0) else spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "tab position")
    val particles = remember { Animatable(1f) }
    var previousKey by remember { mutableStateOf(selectedKey) }
    LaunchedEffect(selectedKey, motion, reduceMotion) {
        val changed = previousKey != selectedKey
        previousKey = selectedKey
        if (changed && motion == NavigationMotion.BUBBLE_FLOAT && !reduceMotion && selectedIndex >= 0) {
            particles.snapTo(0f)
            particles.animateTo(1f, tween(650))
        } else particles.snapTo(1f)
    }
    val flecks = remember { Random(41).let { rng -> List(850) { Triple(rng.nextFloat(), rng.nextFloat(), rng.nextFloat()) } } }
    // Parent provides the usual system navigation inset, as in the existing app.
    BoxWithConstraints(modifier.fillMaxWidth().height(92.dp).padding(horizontal = 12.dp)) {
        val slot = maxWidth / tabs.size
        val hasSelection = selectedIndex >= 0
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val visualPosition = if (rtl) tabs.lastIndex - position else position
        val activeX = slot * (visualPosition + 0.5f)
        Canvas(Modifier.matchParentSize()) {
            val top = 25.dp.toPx(); val bottom = size.height - 6.dp.toPx()
            val r = 17.dp.toPx(); val x = activeX.toPx().coerceIn(r, size.width - r)
            val half = (slot.toPx() * .45f).coerceAtMost(30.dp.toPx())
            val left = (x - half).coerceAtLeast(r); val right = (x + half).coerceAtMost(size.width-r)
            val bump = if (hasSelection && motion == NavigationMotion.LIQUID_BULGE) -15.dp.toPx()
                else if (hasSelection && motion == NavigationMotion.FLOATING_NOTCH) 15.dp.toPx() else 0f
            val shape = Path().apply {
                moveTo(r, top)
                if (bump != 0f) {
                    lineTo(left, top)
                    cubicTo(left + half*.55f, top, x-half*.55f, top+bump, x, top+bump)
                    cubicTo(x+half*.55f, top+bump, right-half*.55f, top, right, top)
                }
                lineTo(size.width-r, top); quadraticTo(size.width, top, size.width, top+r)
                lineTo(size.width,bottom-r); quadraticTo(size.width,bottom,size.width-r,bottom)
                lineTo(r,bottom); quadraticTo(0f,bottom,0f,bottom-r)
                lineTo(0f,top+r); quadraticTo(0f,top,r,top); close()
            }
            drawPath(shape, palette.surface)
            if (palette.felt) {
                // Stable procedural fibers: no per-frame random generation or bitmap assets.
                flecks.forEach { (u,v,k) ->
                    val px = 5.dp.toPx() + u*(size.width-10.dp.toPx())
                    val py = top+19.dp.toPx()+v*(bottom-top-25.dp.toPx())
                    drawLine(if(k>.5f) Color.White.copy(alpha=.11f) else Color.Black.copy(alpha=.10f),
                        Offset(px,py), Offset(px+2.dp.toPx(),py+1.dp.toPx()), .65.dp.toPx())
                }
            }
            drawPath(shape, palette.outline.copy(alpha=.75f), style=Stroke(1.dp.toPx(),
                pathEffect = if(palette.felt) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx())) else null))
            if (hasSelection && motion == NavigationMotion.SLIDING_PILL) {
                val w = minOf(slot.toPx()-5.dp.toPx(), 49.dp.toPx()).coerceAtLeast(1f)
                drawRoundRect(palette.accent, Offset(x-w/2,top+7.dp.toPx()), Size(w,43.dp.toPx()), CornerRadius(20.dp.toPx()))
            }
            if (hasSelection && motion == NavigationMotion.FLOATING_NOTCH) {
                drawCircle(palette.accent, minOf(19.dp.toPx(), half), Offset(x,top-3.dp.toPx()))
                if (palette.felt) drawCircle(palette.onAccent.copy(alpha=.7f), minOf(15.dp.toPx(), half*.8f), Offset(x,top-3.dp.toPx()), style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx()))))
            }
            if(hasSelection && motion == NavigationMotion.BUBBLE_FLOAT && particles.value < 1f) {
                val p=particles.value
                repeat(7) { i ->
                    val spread=(i-3)*5.dp.toPx()*p
                    val y=top+4.dp.toPx()-(22+i%3*7).dp.toPx()*p
                    drawCircle(palette.accent.copy(alpha=(1f-p).coerceIn(0f,1f)), (2+i%3).dp.toPx()*(1f-p*.5f), Offset(x+spread+sin(p*6+i)*3.dp.toPx(),y))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(86.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val emphasis by animateFloatAsState(if(selected) 1f else 0f,
                    if(reduceMotion) tween(0) else spring(stiffness=Spring.StiffnessMedium), label="tab emphasis")
                val lift = when(motion) {
                    NavigationMotion.SLIDING_PILL -> -4.5f
                    NavigationMotion.FLOATING_NOTCH -> -36f
                    NavigationMotion.LIQUID_BULGE -> -10f
                    else -> 0f
                }
                val highlighted = selected && (motion == NavigationMotion.SLIDING_PILL || motion == NavigationMotion.FLOATING_NOTCH || motion == NavigationMotion.EXPANDING_LABEL)
                val tint = if(highlighted) palette.onAccent else if(selected) palette.accent else palette.ink
                Box(Modifier.weight(1f).height(86.dp).selectable(selected=selected, role=Role.Tab, onClick={onSelect(tab.key)}), contentAlignment=Alignment.Center) {
                    val expanding = motion == NavigationMotion.EXPANDING_LABEL && selected
                    Row(Modifier.offset { IntOffset(0, (15.dp.toPx()+lift.dp.toPx()*emphasis).roundToInt()) }
                        .then(if(expanding) Modifier.clip(CircleShape).background(palette.accent).padding(horizontal=4.dp,vertical=7.dp) else Modifier),
                        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
                        Icon(if (motion == NavigationMotion.ICON_FILL && selected) tab.filledIcon else tab.icon,
                            contentDescription=tab.label, tint=tint, modifier=Modifier.size(23.dp))
                        // Keep the label below the icon on narrow phones instead of stealing touch-target width.
                        if(expanding && slot >= 78.dp) Text(tab.label, color=tint, fontSize=10.sp, maxLines=1)
                    }
                    if(selected && (motion==NavigationMotion.ICON_FILL || motion==NavigationMotion.BUBBLE_FLOAT || (motion==NavigationMotion.EXPANDING_LABEL && slot<78.dp))) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom=9.dp)) {
                            if(motion==NavigationMotion.EXPANDING_LABEL) Text(tab.label,color=palette.accent,fontSize=9.sp,maxLines=1)
                            else Box(Modifier.size(if(motion==NavigationMotion.ICON_FILL) 13.dp else 4.dp, 3.dp).background(palette.accent,CircleShape))
                        }
                    }
                }
            }
        }
    }
}

internal val mimicMotionTabs = TopLevel.visible.map { tab ->
    val outline = when (tab) {
        TopLevel.HOME -> Icons.Outlined.Home
        TopLevel.SEARCH -> Icons.Outlined.Search
        TopLevel.LIBRARY -> Icons.Outlined.LibraryMusic
        TopLevel.RADIO -> Icons.Outlined.Radio
        TopLevel.DOWNLOADS -> Icons.Outlined.Download
        TopLevel.SETTINGS -> Icons.Outlined.Settings
    }
    MotionTab(tab.route, tab.label, outline, tab.icon)
}

internal fun NavStyle.navigationMotion(themeId: String): NavigationMotion? =
    NavigationMotion.entries.firstOrNull { it.name == name }
        ?: NavigationMotion.SLIDING_PILL.takeIf {
            this == NavStyle.AUTO && feltNavigationPalette(themeId) != null
        }

internal fun feltNavigationPalette(themeId: String): MotionPalette? = when (themeId) {
    ThemeRegistry.FELT_CLASSIC_ID -> MotionPalette.ClassicFelt
    ThemeRegistry.FELT_ORANGE_ID -> MotionPalette.OrangeFelt
    else -> null
}

@Composable
internal fun MotionPalette.Companion.forTheme(themeId: String): MotionPalette =
    feltNavigationPalette(themeId) ?: MaterialTheme.colorScheme.let { colors ->
        MotionPalette(colors.surface, colors.onSurface, colors.primary, colors.onPrimary, colors.outline)
    }
