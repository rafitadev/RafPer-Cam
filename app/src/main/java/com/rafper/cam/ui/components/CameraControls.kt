package com.rafper.cam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuickToggle(label: String, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (enabled) Color(0xCC323232) else Color(0x40111111), label = "toggle")
    val fg by animateColorAsState(if (enabled) Color(0xFFFFDE59) else Color.White, label = "toggle-fg")
    Box(
        modifier = Modifier
            .background(bg, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ModeSelector(modes: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        modes.forEachIndexed { index, mode ->
            val alpha by animateFloatAsState(if (index == selected) 1f else 0.55f, label = "mode")
            Text(mode, color = Color.White.copy(alpha = alpha), modifier = Modifier.clickable { onSelected(index) })
        }
    }
}
