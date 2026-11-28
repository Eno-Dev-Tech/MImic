package com.example.mimic.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.mimic.player.rememberMediaVolume
import com.example.mimic.ui.designsystem.plasmaProgressBrush
import com.example.mimic.ui.designsystem.plasmaSurface
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlasmaVolumeWave() {
    val volume = rememberMediaVolume()
    val percentage = "${(volume.fraction * 100).roundToInt()}%"
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier.fillMaxWidth().plasmaSurface(RoundedCornerShape(18.dp), colored = false)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Volume", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(percentage, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = volume.fraction,
            onValueChange = volume::setFraction,
            enabled = !volume.fixed,
            steps = (volume.max - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Media volume"
                stateDescription = percentage
            },
            thumb = {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(width = 5.dp, height = 25.dp)
                        .background(Color(0xFFE7ECFF).copy(alpha = if (volume.fixed) .45f else 1f), CircleShape),
                )
            },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val brush = plasmaProgressBrush()
                    for (bar in 0..42) {
                        val fraction = bar / 42f
                        val x = size.width * if (rtl) 1f - fraction else fraction
                        val amplitude = .18f + .67f * abs(sin(fraction * 12f + .4f) * sin(fraction * 25f + 1f))
                        val half = size.height * amplitude / 2f
                        val top = Offset(x, center.y - half)
                        val bottom = Offset(x, center.y + half)
                        if (fraction <= state.value && !volume.fixed) {
                            drawLine(brush, top, bottom, 4.dp.toPx(), StrokeCap.Round, alpha = .18f)
                            drawLine(brush, top, bottom, 2.dp.toPx(), StrokeCap.Round)
                        } else {
                            drawLine(Color(0xFF555064), top, bottom, 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            },
        )
    }
}
