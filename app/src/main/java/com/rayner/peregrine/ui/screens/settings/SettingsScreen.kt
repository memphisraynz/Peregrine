package com.rayner.peregrine.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onViewLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            item { SettingsSectionHeader("Server") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Dns,
                        title = "Frigate server",
                        subtitle = uiState.serverUrl,
                        onClick = { /* Edit URL */ }
                    )
                }
            }

            item { SettingsSectionHeader("Cameras") }
            item {
                SettingsGroup {
                    SettingsDropdownRow(
                        icon = Icons.Default.Videocam,
                        title = "Player type",
                        selectedOption = if (uiState.defaultPlayerType == "hls") "HLS (Stable)" else "WebRTC (Low latency)",
                        options = listOf("HLS (Stable)", "WebRTC (Low latency)"),
                        onOptionSelected = { option ->
                            val type = if (option == "HLS (Stable)") "hls" else "webrtc"
                            viewModel.setDefaultPlayerType(type)
                        }
                    )
                    SettingsDropdownRow(
                        icon = Icons.Default.Palette,
                        title = "VOD Buffer",
                        selectedOption = "${uiState.vodBuffer} seconds",
                        options = listOf("0 seconds", "5 seconds", "10 seconds", "15 seconds"),
                        onOptionSelected = { option ->
                            val seconds = option.split(" ")[0].toInt()
                            viewModel.setVodBuffer(seconds)
                        }
                    )
                    SettingsDropdownRow(
                        icon = Icons.Default.Notifications,
                        title = "Alerts filter",
                        selectedOption = when (uiState.alertsFilterDays) {
                            0 -> "All"
                            -12 -> "Last 12 hours"
                            1 -> "Last 24 hours"
                            else -> "Last ${uiState.alertsFilterDays} days"
                        },
                        options = listOf("All", "Last 12 hours", "Last 24 hours", "Last 2 days", "Last 3 days"),
                        onOptionSelected = { option ->
                            val days = when (option) {
                                "All" -> 0
                                "Last 12 hours" -> -12
                                "Last 24 hours" -> 1
                                "Last 2 days" -> 2
                                "Last 3 days" -> 3
                                else -> 1
                            }
                            viewModel.setAlertsFilterDays(days)
                        }
                    )
                    SettingsToggleRow(
                        icon = Icons.Default.Notifications,
                        title = "Enable notifications",
                        checked = true,
                        onCheckedChange = { /* Toggle */ }
                    )
                }
            }

            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsGroup {
                    SettingsToggleRow(
                        icon = Icons.Default.Palette,
                        title = "Dynamic color",
                        subtitle = "Harmonize with wallpaper",
                        checked = true,
                        onCheckedChange = { /* Toggle */ }
                    )
                }
            }

            item { SettingsSectionHeader("System") }
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        title = "View logs",
                        onClick = onViewLogs
                    )
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "About Peregrine",
                        subtitle = "Version 1.0.0",
                        onClick = { /* Show about */ }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Button(
                    onClick = { viewModel.onLogout(onLogout) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
fun SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) },
            supportingContent = { Text(selectedOption, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)) },
            leadingContent = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge, // ~28dp or 16dp per guide
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)) } },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)) } },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.onPrimary, // Pop
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}
