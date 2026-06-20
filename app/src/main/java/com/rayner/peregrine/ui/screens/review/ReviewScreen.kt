package com.rayner.peregrine.ui.screens.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Review") })
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(uiState.reviewItems) { item ->
                    ReviewItemCard(item)
                }
            }
        }
    }
}

@Composable
fun ReviewItemCard(item: Map<String, Any>) {
    val camera = item["camera"] as? String ?: "Unknown"
    val label = item["label"] as? String ?: "Object"
    val startTime = item["start_time"]?.toString() ?: ""

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "$label detected on $camera", style = MaterialTheme.typography.titleMedium)
            Text(text = "Time: $startTime", style = MaterialTheme.typography.bodySmall)
        }
    }
}
