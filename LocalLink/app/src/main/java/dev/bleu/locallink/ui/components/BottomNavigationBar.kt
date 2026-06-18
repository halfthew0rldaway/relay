package dev.bleu.locallink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.locallink.ui.theme.Primary
import dev.bleu.locallink.ui.theme.SurfaceContainerHighest
import dev.bleu.locallink.ui.theme.SurfaceContainerLow

enum class NavTab { TRANSFER, HISTORY, SETTINGS }

@Composable
fun BottomNavigationBar(
    active: NavTab,
    onTransfer: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavItem(
                icon = Icons.Filled.Wifi,
                label = "Transfer",
                selected = active == NavTab.TRANSFER,
                modifier = Modifier.weight(1f),
                onClick = onTransfer
            )
            NavItem(
                icon = Icons.Filled.History,
                label = "History",
                selected = active == NavTab.HISTORY,
                modifier = Modifier.weight(1f),
                onClick = onHistory
            )
            NavItem(
                icon = Icons.Filled.Settings,
                label = "Settings",
                selected = active == NavTab.SETTINGS,
                modifier = Modifier.weight(1f),
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) Primary else Color.Transparent
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
