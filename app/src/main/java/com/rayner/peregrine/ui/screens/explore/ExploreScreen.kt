package com.rayner.peregrine.ui.screens.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.ui.theme.DetectionColors
import com.rayner.peregrine.util.formatCameraName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredEvents = uiState.filteredEvents

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Explore", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Filter chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = { /* Open filters sheet */ },
                            label = { Text("Filters") },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null
                        )
                    }

                    items(uiState.labels) { label ->
                        val isSelected = uiState.selectedLabels.contains(label)
                        val detectionColors = when (label.lowercase()) {
                            "person" -> DetectionColors.Person
                            "car", "vehicle" -> DetectionColors.Vehicle
                            "animal" -> DetectionColors.Animal
                            else -> DetectionColors.Person
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleLabel(label) },
                            label = { Text(label.replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = detectionColors.container,
                                selectedLabelColor = detectionColors.onContainer,
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = if (!isSelected) FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.outline,
                                enabled = true,
                                selected = false
                            ) else null
                        )
                    }
                }

                // Count + Sort Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredEvents.size} events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Sort action */ }
                    ) {
                        Text(
                            text = "Newest",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (filteredEvents.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No events found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredEvents, key = { it.id }) { event ->
                            val isHighlighted = uiState.highlightedEventId == event.id
                            EventGridCard(
                                event = event,
                                imageLoader = viewModel.imageLoader,
                                isHighlighted = isHighlighted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventGridCard(
    event: ExploreEventEntity,
    imageLoader: coil3.ImageLoader,
    isHighlighted: Boolean = false
) {
    val camera = event.camera
    val label = event.label
    val startTime = event.startTime
    val thumbUrl = event.thumbUrl

    val timeStr = remember(startTime) {
        val date = java.util.Date((startTime * 1000).toLong())
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(date)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square-ish cards for grid
            .clickable { /* Navigate to event detail */ },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isHighlighted) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = thumbUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Overlays
            DetectionChipVerySmall(
                label = label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = formatCameraName(camera),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun DetectionChipVerySmall(label: String, modifier: Modifier = Modifier) {
    val colors = when (label.lowercase()) {
        "person" -> DetectionColors.Person
        "car", "vehicle" -> DetectionColors.Vehicle
        "animal" -> DetectionColors.Animal
        else -> DetectionColors.Person
    }

    Surface(
        color = colors.container,
        shape = RoundedCornerShape(7.dp),
        modifier = modifier
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = colors.onContainer,
            fontSize = 9.sp
        )
    }
}
