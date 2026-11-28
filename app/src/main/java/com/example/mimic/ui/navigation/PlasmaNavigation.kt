package com.example.mimic.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimic.ui.designsystem.plasmaSurface
import com.example.mimic.ui.designsystem.AuroraIcon

private val DockShape = RoundedCornerShape(24.dp)
private val TabShape = RoundedCornerShape(15.dp)

@Composable
internal fun PlasmaNavigation(
    selected: TopLevel?,
    onSelect: (TopLevel) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .plasmaSurface(DockShape)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in TopLevel.visible) {
            val active = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val highlight by animateFloatAsState(if (active || pressed || focused) 1f else 0f, label = "Plasma tab")
            Column(
                Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 58.dp)
                    .then(if (compact) Modifier.semantics { contentDescription = tab.label } else Modifier)
                    .selectable(active, interaction, indication = null, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.size(width = 40.dp, height = 32.dp)
                        .background(Color(0xFF11111B).copy(alpha = .74f * highlight), TabShape)
                        .border(1.dp, Color(0xFF454255).copy(alpha = .66f * highlight), TabShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AuroraIcon(tab.icon, null, 21.dp, highlight)
                }
                if (!compact) {
                    Text(tab.label, fontSize = 9.sp, lineHeight = 12.sp,
                        color = if (active) Color(0xFFC6F4FF) else Color(0xFFB8AFCB),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
