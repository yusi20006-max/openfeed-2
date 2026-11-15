package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.data.model.StarterChannels
import com.example.data.model.TransportMode
import com.example.ui.FeedFilterMode
import com.example.ui.FeedUiState
import com.example.ui.MediaViewerState
import com.example.ui.components.PostCard

@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onSetFilterMode: (FeedFilterMode) -> Unit,
    onSelectChannelChip: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenAddChannel: () -> Unit,
    onQuickAddChannel: (String) -> Unit,
    onOpenChannelDetail: (String) -> Unit,
    onToggleBookmark: (FeedPost) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearch by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.RssFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = buildString {
                                        append(
                                            when (uiState.settings.transportMode) {
                                                TransportMode.AUTO -> "Auto Bridge"
                                                TransportMode.TRANSLATE_BRIDGE -> "Translate Bridge"
                                                TransportMode.DIRECT -> "Direct t.me"
                                                TransportMode.CUSTOM_MIRROR -> "Custom Mirror"
                                            }
                                        )
                                        if (uiState.settings.useDoH) append(" • DoH")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) onSearchQueryChange("")
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("feed_search_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onRefreshAll,
                            enabled = !uiState.isRefreshing,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("feed_refresh_button")
                        ) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.refresh_feed),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.search_posts_hint)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("feed_search_input")
                    )
                }

                if (uiState.channels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.filterMode == FeedFilterMode.ALL && uiState.selectedChannelChip == null,
                                onClick = {
                                    onSetFilterMode(FeedFilterMode.ALL)
                                    onSelectChannelChip(null)
                                },
                                label = { Text(stringResource(R.string.tab_all)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_all")
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.filterMode == FeedFilterMode.FAVORITES,
                                onClick = { onSetFilterMode(FeedFilterMode.FAVORITES) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_favorites)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_favorites")
                            )
                        }
                        items(uiState.channels, key = { it.username }) { ch ->
                            val selected = uiState.selectedChannelChip.equals(ch.username, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onSetFilterMode(FeedFilterMode.ALL)
                                    onSelectChannelChip(ch.username)
                                },
                                label = {
                                    Text(
                                        text = "@${ch.username}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("channel_chip_${ch.username}")
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isRefreshing || uiState.isAddingChannel) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (uiState.posts.isEmpty()) {
            FeedEmptyState(
                hasChannels = uiState.channels.isNotEmpty(),
                isLoading = uiState.isAddingChannel || uiState.isRefreshing,
                onOpenAddChannel = onOpenAddChannel,
                onQuickAddChannel = onQuickAddChannel,
                onRefreshAll = onRefreshAll,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = uiState.posts,
                    key = { it.uniqueKey }
                ) { post ->
                    PostCard(
                        post = post,
                        transportMode = uiState.settings.transportMode,
                        autoLoadMedia = uiState.settings.autoLoadMedia,
                        fontScale = uiState.settings.fontScale,
                        downloadingKey = uiState.downloadingKey,
                        activeAudioUrl = uiState.activeAudioUrl,
                        onChannelClick = onOpenChannelDetail,
                        onMentionClick = { mention ->
                            onOpenChannelDetail(mention.removePrefix("@"))
                        },
                        onToggleBookmark = onToggleBookmark,
                        onOpenMedia = onOpenMedia,
                        onToggleAudio = onToggleAudio,
                        onDownloadMedia = onDownloadMedia,
                        onCopiedToast = onShowMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedEmptyState(
    hasChannels: Boolean,
    isLoading: Boolean,
    onOpenAddChannel: () -> Unit,
    onQuickAddChannel: (String) -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Image(
                painter = painterResource(id = R.drawable.img_empty_feed),
                contentDescription = "OpenFeed illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasChannels) "No matching posts yet" else stringResource(R.string.empty_feed_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasChannels) {
                    "Try refreshing your subscribed channels or clearing your search filter."
                } else {
                    stringResource(R.string.empty_feed_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenAddChannel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_add_channel_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_channel_title))
                }
                if (hasChannels) {
                    OutlinedButton(
                        onClick = onRefreshAll,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.refresh_feed))
                    }
                }
            }
        }

        if (!hasChannels) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tap any channel below to fetch live via OpenFeed Engine",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(StarterChannels.list, key = { it.username }) { suggested ->
                Surface(
                    onClick = { if (!isLoading) onQuickAddChannel(suggested.username) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_starter_${suggested.username}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = suggested.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = suggested.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "@${suggested.username} — ${suggested.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Subscribe",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
