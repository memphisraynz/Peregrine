package com.rayner.peregrine.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.messaging.FirebaseMessaging

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onViewLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.2"
        } catch (e: Exception) {
            "1.0.2"
        }
    }
    var fcmToken by remember { mutableStateOf("Fetching...") }

    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
                android.util.Log.d("PeregrineFCM", "Token: $fcmToken")
            } else {
                fcmToken = "Failed to fetch"
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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

            item { SettingsSectionHeader("Player Settings") }
            item {
                SettingsGroup {
                    val playerOptions = listOf("MSE", "WebRTC", "HLS")
                    val availableOptions = playerOptions.filter {
                        when (it) {
                            "MSE" -> uiState.isMseEnabled
                            "WebRTC" -> uiState.isWebRtcEnabled
                            "HLS" -> uiState.isHlsEnabled
                            else -> false
                        }
                    }.ifEmpty { listOf("MSE") }

                    SettingsDropdownRow(
                        icon = Icons.Default.Videocam,
                        title = "Default player",
                        selectedOption = uiState.defaultPlayerType.uppercase(),
                        options = availableOptions,
                        onOptionSelected = { viewModel.setDefaultPlayerType(it.lowercase()) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    val fallbackOptions = mutableListOf("None")
                    if (uiState.isHlsEnabled) fallbackOptions.add("HLS")
                    if (uiState.isMseEnabled) fallbackOptions.add("MSE")
                    if (uiState.isWebRtcEnabled) fallbackOptions.add("WebRTC")

                    SettingsDropdownRow(
                        icon = Icons.Default.Videocam,
                        title = "Fallback player",
                        selectedOption = uiState.fallbackPlayerType.replaceFirstChar { it.uppercase() },
                        options = fallbackOptions,
                        onOptionSelected = { viewModel.setFallbackPlayerType(it.lowercase()) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        icon = Icons.Default.Videocam,
                        title = "Enable MSE",
                        subtitle = "Fast live streaming",
                        checked = uiState.isMseEnabled,
                        onCheckedChange = { viewModel.setPlayerEnabled("mse", it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        icon = Icons.Default.Videocam,
                        title = "Enable WebRTC",
                        subtitle = "Low latency bidirectional audio",
                        checked = uiState.isWebRtcEnabled,
                        onCheckedChange = { viewModel.setPlayerEnabled("webrtc", it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsToggleRow(
                        icon = Icons.Default.Videocam,
                        title = "Enable HLS",
                        subtitle = "Legacy high latency streaming",
                        checked = uiState.isHlsEnabled,
                        onCheckedChange = { viewModel.setPlayerEnabled("hls", it) }
                    )
                }
            }

            item { SettingsSectionHeader("Review Settings") }
            item {
                SettingsGroup {
                    SettingsDropdownRow(
                        icon = Icons.Default.History,
                        title = "VOD Buffer",
                        selectedOption = "${uiState.vodBuffer} seconds",
                        options = listOf("0 seconds", "5 seconds", "10 seconds", "15 seconds"),
                        onOptionSelected = { option ->
                            val seconds = option.split(" ")[0].toInt()
                            viewModel.setVodBuffer(seconds)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsDropdownRow(
                        icon = Icons.Default.FilterList,
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
                }
            }

            item { SettingsSectionHeader("Notifications") }
            item {
                SettingsGroup {
                    SettingsToggleRow(
                        icon = Icons.Default.Notifications,
                        title = "Show latest only",
                        subtitle = "Group alerts by camera/category",
                        checked = uiState.showLatestOnly,
                        onCheckedChange = { viewModel.setShowLatestOnly(it) }
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "About Peregrine",
                        subtitle = "Version $versionName",
                        onClick = { /* Show about */ }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsRow(
                        icon = Icons.Default.ContentCopy,
                        title = "FCM Registration Token",
                        subtitle = fcmToken,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("FCM Token", fcmToken)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        title = "Register for Notifications",
                        subtitle = "Send FCM token to Frigate server",
                        onClick = {
                            if (fcmToken != "Fetching..." && fcmToken != "Failed to fetch") {
                                viewModel.registerFcmToken(fcmToken)
                                Toast.makeText(context, "Registration request sent", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "FCM token not available", Toast.LENGTH_SHORT).show()
                            }
                        }
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
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                selectedOption,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.primary)
                }
            },
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.equals(selectedOption, ignoreCase = true)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    showDialog = false
                                }
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
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
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
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
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}
