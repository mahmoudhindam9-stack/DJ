package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DjBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        DjNavItem(
            icon = Icons.Filled.PlayArrow,
            label = "Player",
            selected = currentRoute == "player",
            onClick = { onNavigate("player") }
        )
        DjNavItem(
            icon = Icons.Filled.Headset,
            label = "Mixer",
            selected = currentRoute == "dj",
            onClick = { onNavigate("dj") }
        )
        DjNavItem(
            icon = Icons.Filled.Tune,
            label = "EQ",
            selected = currentRoute == "equalizer",
            onClick = { onNavigate("equalizer") }
        )
        DjNavItem(
            icon = Icons.Filled.MusicNote,
            label = "Studio",
            selected = currentRoute == "studio",
            onClick = { onNavigate("studio") }
        )
        DjNavItem(
            icon = Icons.Filled.Cloud,
            label = "Online",
            selected = currentRoute == "online_music",
            onClick = { onNavigate("online_music") }
        )
        DjNavItem(
            icon = Icons.Filled.Mic,
            label = "Mic",
            selected = currentRoute == "mic",
            onClick = { onNavigate("mic") }
        )
    }
}

@Composable
private fun RowScope.DjNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        icon = { 
            Icon(
                imageVector = icon, 
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        label = { 
            Text(
                text = label, 
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary,
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
