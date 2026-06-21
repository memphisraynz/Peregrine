package com.rayner.peregrine.ui.screens.review

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onItemClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Review") })
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(uiState.reviewItems) { item ->
                    val id = item["id"] as? String ?: ""
                    ReviewItemCard(
                        item = item,
                        baseUrl = uiState.baseUrl,
                        imageLoader = viewModel.imageLoader,
                        onClick = { onItemClick(id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewItemCard(
    item: Map<String, Any>,
    baseUrl: String,
    imageLoader: coil3.ImageLoader,
    onClick: () -> Unit
) {
    val camera = item["camera"] as? String ?: "Unknown"
    val severity = item["severity"] as? String ?: "review"
    val data = item["data"] as? Map<*, *>
    val objects = data?.get("objects") as? List<*>
    val label = objects?.firstOrNull()?.toString()?.replaceFirstChar { it.uppercase() }
        ?: severity.replaceFirstChar { it.uppercase() }
    
    val startTime = item["start_time"]?.toString() ?: ""
    val thumbPath = item["thumb_path"] as? String ?: ""
    
    // Revert to working mapping: /media/frigate/clips -> /clips
    val normalizedPath = thumbPath.replace("/media/frigate", "")
    val fullThumbUrl = if (normalizedPath.startsWith("/")) {
        "$baseUrl$normalizedPath"
    } else {
        "$baseUrl/$normalizedPath"
    }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            AsyncImage(
                model = fullThumbUrl,
                imageLoader = imageLoader,
                contentDescription = "$camera review thumbnail",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.77f),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleLarge)
                    val isRetained = item["retain_indefinitely"] as? Boolean ?: false
                    if (isRetained) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Retained") }
                        )
                    }
                }
                Text(text = "Camera: $camera", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Started: $startTime", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
