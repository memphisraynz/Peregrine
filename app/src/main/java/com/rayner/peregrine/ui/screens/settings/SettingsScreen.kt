package com.rayner.peregrine.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Server Configuration", style = MaterialTheme.typography.titleMedium)
            
            ListItem(
                headlineContent = { Text("Server URL") },
                supportingContent = { Text(uiState.serverUrl) },
                leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) }
            )

            ListItem(
                headlineContent = { Text("Username") },
                supportingContent = { Text(uiState.username.ifBlank { "Not set" }) },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            HorizontalDivider()

            Text(text = "Diagnostics", style = MaterialTheme.typography.titleMedium)

            ListItem(
                headlineContent = { Text("View Server Logs") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onViewLogs)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.onLogout(onLogout) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }
}
