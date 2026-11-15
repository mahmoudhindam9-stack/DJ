package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DjBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .shadow(24.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        tonalElevation = 8.dp,
        shadowElevation = 18.dp
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            DjNavItem(Icons.Filled.PlayArrow, "Player", "Player", currentRoute == "player", "nav_player") { onNavigate("player") }
            DjNavItem(Icons.Filled.Tune, "EQ", "Equalizer", currentRoute == "equalizer", "nav_equalizer") { onNavigate("equalizer") }
            DjNavItem(Icons.Filled.Cloud, "Online", "Online Music", currentRoute == "online_music", "nav_online_music") { onNavigate("online_music") }
            DjNavItem(Icons.Filled.Radio, "Radio", "Radio", currentRoute == "radio", "nav_radio") { onNavigate("radio") }
            DjNavItem(Icons.Filled.MenuBook, "Quran", "Holy Quran", currentRoute == "quran", "nav_quran") { onNavigate("quran") }
            DjNavItem(Icons.Filled.Headset, "DJ", "DJ Mixer", currentRoute == "dj", "nav_dj") { onNavigate("dj") }
            DjNavItem(Icons.Filled.Mic, "Mic", "Mic/Karaoke", currentRoute == "mic", "nav_mic") { onNavigate("mic") }
        }
    }
}

@Composable
private fun RowScope.DjNavItem(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        modifier = Modifier.testTag(tag),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        },
        alwaysShowLabel = true,
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

