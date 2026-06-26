package com.rayner.peregrine.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.ui.theme.DetectionColors
import com.rayner.peregrine.util.formatCameraName
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel(),
    onItemClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredItems = uiState.filteredItems

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Review", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Segmented Control
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ReviewTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ReviewTab.entries.size),
                            onClick = { viewModel.setTab(tab) },
                            selected = uiState.selectedTab == tab,
                            icon = {
                                if (uiState.selectedTab == tab) {
                                    SegmentedButtonDefaults.Icon(active = true) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                        )
                                    }
                                }
                            },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeBorderColor = MaterialTheme.colorScheme.outline,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        ) {
                            Text(tab.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                if (filteredItems.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No items found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val groupedItems = groupReviewItemsByDate(filteredItems)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        groupedItems.forEach { (dateHeader, items) ->
                            item(key = dateHeader) {
                                DateHeader(dateHeader)
                            }
                            items(items, key = { it.id }) { item ->
                                ReviewItemCard(
                                    item = item,
                                    baseUrl = uiState.baseUrl,
                                    imageLoader = viewModel.imageLoader,
                                ) { onItemClick(item.id) }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
fun ReviewItemCard(
    item: ReviewItemEntity,
    baseUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val camera = item.camera
    val displayLabel = getDisplayLabel(item)
    
    val startTime = item.startTime
    val thumbPath = item.thumbPath
    val hasBeenReviewed = item.hasBeenReviewed

    val formattedTime = remember(startTime) {
        val date = Date((startTime * 1000).toLong())
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }

    val normalizedPath = thumbPath.replace("/media/frigate", "")
    val fullThumbUrl = if (normalizedPath.startsWith("/")) {
        "$baseUrl$normalizedPath"
    } else {
        "$baseUrl/$normalizedPath"
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(modifier = Modifier.aspectRatio(16 / 9f)) {
            AsyncImage(
                model = fullThumbUrl,
                imageLoader = imageLoader,
                contentDescription = "$camera review thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // Category chip top-left
            DetectionChip(
                label = displayLabel,
                colors = getDetectionColors(item),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )

            // Camera name pill bottom-left
            Surface(
                color = Color(0x9E141218),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = formatCameraName(camera),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Time bottom-right
            Text(
                text = formattedTime,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White, // High contrast on image
            )

            // Unreviewed dot top-right
            if (!hasBeenReviewed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@Composable
fun DetectionChip(label: String, colors: DetectionColors.Pair, modifier: Modifier = Modifier) {
    Surface(
        color = colors.container,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onContainer,
        )
    }
}

fun groupReviewItemsByDate(items: List<ReviewItemEntity>): Map<String, List<ReviewItemEntity>> {
    val grouped = mutableMapOf<String, MutableList<ReviewItemEntity>>()
    val today = Calendar.getInstance().apply { 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val yesterday = today - (24 * 60 * 60 * 1000)

    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

    items.forEach { item ->
        val startTime = item.startTime * 1000
        val date = Date(startTime.toLong())
        
        val header = when {
            startTime >= today -> "Today"
            startTime >= yesterday -> "Yesterday"
            else -> dateFormat.format(date)
        }
        
        grouped.getOrPut(header) { mutableListOf() }.add(item)
    }
    
    return grouped
}

private fun getDisplayLabel(review: ReviewItemEntity): String {
    val subLabel = review.subLabels.firstOrNull()
    return if ((review.objects.contains("person-verified")) && (subLabel != null)) {
        subLabel
    } else {
        review.primaryLabel ?: review.severity
    }
}

private fun getDetectionColors(review: ReviewItemEntity): DetectionColors.Pair {
    val subLabel = review.subLabels.firstOrNull()
    if ((review.objects.contains("person-verified")) && (subLabel != null)) {
        return DetectionColors.Verified
    }
    return when (review.primaryLabel?.lowercase()) {
        "person" -> DetectionColors.Person
        "car", "vehicle", "truck", "bus" -> DetectionColors.Vehicle
        "dog", "cat", "animal" -> DetectionColors.Animal
        else -> DetectionColors.Person
    }
}
