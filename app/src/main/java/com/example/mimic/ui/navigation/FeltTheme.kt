package com.example.mimic.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

internal val LocalFelt = staticCompositionLocalOf { false }

/** Reusable textured control/card surface; caller supplies click semantics and content. */
@Composable
fun FeltSurface(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface,
    stitch: Color = MaterialTheme.colorScheme.outline, elevation: Dp = 5.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    val fibers = remember { Random(82).let { random -> List(900) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) } } }
    Box(modifier.shadow(elevation, shape).clip(shape).background(color), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val fiberCount = (size.width * size.height / 18.dp.toPx()).toInt().coerceIn(0, fibers.size)
            for (index in 0 until fiberCount) {
                val (x, y, shade) = fibers[index]
                val start=Offset(x*size.width,y*size.height)
                drawLine(if (shade > .5f) Color.White.copy(alpha=.13f) else Color.Black.copy(alpha=.12f),
                    start,start+Offset(2.dp.toPx(),1.dp.toPx()), .6.dp.toPx())
            }
            val inset=4.dp.toPx()
            if(size.width>2*inset && size.height>2*inset) drawRoundRect(stitch,
                topLeft=Offset(inset,inset),size=Size(size.width-2*inset,size.height-2*inset),cornerRadius=CornerRadius(13.dp.toPx()),
                style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx()))))
        }
        content()
    }
}
