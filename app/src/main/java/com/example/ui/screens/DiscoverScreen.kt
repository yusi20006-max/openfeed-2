package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedSourceType
import com.example.data.model.StarterChannels
import com.example.data.model.SuggestedChannel
import com.example.ui.FeedUiState
import com.example.ui.components.ChannelAvatar

private enum class DiscoverTab {
    TELEGRAM,
    RSS
}

@Composable
fun DiscoverScreen(
    uiState: FeedUiState,
    onSubscribeSuggested: (SuggestedChannel) -> Unit,
    onOpenChannelDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by rememberSaveable { mutableStateOf(DiscoverTab.TELEGRAM) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val subscribedMap = remember(uiState.channels) {
        uiState.channels.associateBy { it.username.lowercase() }
    }

    val sourceList = remember(activeTab) {
        when (activeTab) {
            DiscoverTab.TELEGRAM -> StarterChannels.persianTelegramChannels
            DiscoverTab.RSS -> StarterChannels.rssFeeds
        }
    }

    val categories = remember(sourceList) {
        sourceList.map { it.category }.distinct()
    }

    val filteredList = remember(sourceList, selectedCategory, searchQuery) {
        val byCat = if (selectedCategory == null || selectedCategory !in categories) {
            sourceList
        } else {
            sourceList.filter { it.category == selectedCategory }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            byCat
        } else {
            byCat.filter { item ->
                item.title.contains(q, ignoreCase = true) ||
                    item.username.contains(q, ignoreCase = true) ||
                    item.description.contains(q, ignoreCase = true) ||
                    item.category.contains(q, ignoreCase = true) ||
                    (item.feedUrl?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_discover),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.discover_subtitle,
                        StarterChannels.persianTelegramChannels.size,
                        StarterChannels.rssFeeds.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = activeTab == DiscoverTab.TELEGRAM,
                        onClick = {
                            activeTab = DiscoverTab.TELEGRAM
                            selectedCategory = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("discover_tab_telegram")
                    ) {
                        Text(
                            text = "Telegram (${StarterChannels.persianTelegramChannels.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SegmentedButton(
                        selected = activeTab == DiscoverTab.RSS,
                        onClick = {
                            activeTab = DiscoverTab.RSS
                            selectedCategory = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.RssFeed,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("discover_tab_rss")
                    ) {
                        Text(
                            text = "RSS (${StarterChannels.rssFeeds.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.discover_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text(stringResource(R.string.tab_all)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("discover_cat_all")
                        )
                    }
                    items(categories) { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategory = if (selected) null else category
                            },
                            label = { Text(category) },
                            modifier = Modifier.testTag("discover_cat_$category")
                        )
                    }
                }
            }
        }

        if (uiState.isAddingChannel) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = filteredList,
                key = { "${it.sourceType.name}_${it.username}" }
            ) { suggested ->
                val isSubscribed = suggested.username.lowercase() in subscribedMap
                DiscoverFeedItemCard(
                    suggested = suggested,
                    isSubscribed = isSubscribed,
                    isBusy = uiState.isAddingChannel,
                    transportMode = uiState.settings.transportMode,
                    onSubscribe = { onSubscribeSuggested(suggested) },
                    onOpen = { onOpenChannelDetail(suggested.username) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverFeedItemCard(
    suggested: SuggestedChannel,
    isSubscribed: Boolean,
    isBusy: Boolean,
    transportMode: com.example.data.model.TransportMode,
    onSubscribe: () -> Unit,
    onOpen: () -> Unit
) {
    val subtitleMeta = remember(suggested) {
        if (suggested.sourceType == FeedSourceType.RSS) {
            val host = runCatching { Uri.parse(suggested.feedUrl).host?.removePrefix("www.") }.getOrNull()
            host ?: "RSS Feed"
        } else {
            "@${suggested.username}"
        }
    }

    Surface(
        onClick = {
            if (isSubscribed) {
                onOpen()
            } else if (!isBusy) {
                onSubscribe()
            }
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSubscribed) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discover_item_${suggested.username}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatar(
                title = suggested.title,
                avatarUrl = suggested.logoUrl,
                transportMode = transportMode,
                sizeDp = 48,
                sourceType = suggested.sourceType,
                username = suggested.username
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = suggested.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Content
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = if (suggested.sourceType == FeedSourceType.RSS) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = suggested.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (suggested.sourceType == FeedSourceType.RSS) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleMeta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (suggested.sourceType == FeedSourceType.RSS) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = suggested.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = TextDirection.Content
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (isSubscribed) {
                OutlinedButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("discover_open_${suggested.username}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.subscribed_label),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.subscribed_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onSubscribe,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("discover_subscribe_${suggested.username}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_channel_button),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.add_channel_button),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
