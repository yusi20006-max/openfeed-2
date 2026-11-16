package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChannelInfo
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.data.model.TransportMode
import com.example.ui.FeedFilterMode
import com.example.ui.FeedUiState
import com.example.ui.MediaViewerState
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.PostCard
import com.example.ui.theme.resolveIsDarkTheme
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onSetFilterMode: (FeedFilterMode) -> Unit,
    onSelectChannelChip: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenAddChannel: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenChannelDetail: (String) -> Unit,
    onToggleBookmark: (FeedPost) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    httpClient: OkHttpClient? = null,
    onMarkPostRead: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onOpenArticle: ((FeedPost, String, String) -> Unit)? = null
) {
    var showSearch by remember { mutableStateOf(false) }
    val isDark = resolveIsDarkTheme(uiState.settings.themeMode)

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.totalUnreadCount > 0) {
                            IconButton(
                                onClick = onMarkAllRead,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("feed_mark_all_read_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = stringResource(R.string.mark_all_read),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { onToggleDarkMode(isDark) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("feed_dark_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = stringResource(R.string.dark_mode_toggle),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                    SubscribedLogosStrip(
                        channels = uiState.channels,
                        unreadCounts = uiState.unreadCounts,
                        selectedChannelKey = uiState.selectedChannelChip,
                        transportMode = uiState.settings.transportMode,
                        onSelectChannel = { key ->
                            onSetFilterMode(FeedFilterMode.ALL)
                            onSelectChannelChip(key)
                        },
                        onOpenChannelDetail = onOpenChannelDetail
                    )

                    Spacer(modifier = Modifier.height(8.dp))
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
                            val unreadLabel = if (uiState.totalUnreadCount > 0) {
                                "${stringResource(R.string.tab_unread)} (${uiState.totalUnreadCount})"
                            } else {
                                stringResource(R.string.tab_unread)
                            }
                            FilterChip(
                                selected = uiState.filterMode == FeedFilterMode.UNREAD,
                                onClick = { onSetFilterMode(FeedFilterMode.UNREAD) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.MarkEmailUnread,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(unreadLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_unread")
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
                        item {
                            FilterChip(
                                selected = uiState.filterMode == FeedFilterMode.TELEGRAM,
                                onClick = {
                                    onSetFilterMode(FeedFilterMode.TELEGRAM)
                                    onSelectChannelChip(null)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Forum,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Telegram") },
                                modifier = Modifier.testTag("filter_chip_telegram")
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.filterMode == FeedFilterMode.RSS,
                                onClick = {
                                    onSetFilterMode(FeedFilterMode.RSS)
                                    onSelectChannelChip(null)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.RssFeed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("RSS") },
                                modifier = Modifier.testTag("filter_chip_rss")
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isRefreshing || uiState.isAddingChannel) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefreshAll,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (uiState.posts.isEmpty()) {
                    FeedEmptyState(
                        hasChannels = uiState.channels.isNotEmpty(),
                        onOpenDiscover = onOpenDiscover,
                        onOpenAddChannel = onOpenAddChannel,
                        onRefreshAll = onRefreshAll,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize(),
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
                                onCopiedToast = onShowMessage,
                                httpClient = httpClient,
                                onPostInteracted = { onMarkPostRead(it.uniqueKey) },
                                onOpenArticle = onOpenArticle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscribedLogosStrip(
    channels: List<ChannelInfo>,
    unreadCounts: Map<String, Int>,
    selectedChannelKey: String?,
    transportMode: TransportMode,
    onSelectChannel: (String?) -> Unit,
    onOpenChannelDetail: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(channels, key = { "strip_${it.username}" }) { ch ->
            val selected = selectedChannelKey.equals(ch.username, ignoreCase = true)
            val unread = unreadCounts[ch.username] ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (selected) {
                            onOpenChannelDetail(ch.username)
                        } else {
                            onSelectChannel(ch.username)
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .testTag("home_channel_logo_${ch.username}")
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        ChannelAvatar(
                            title = ch.title,
                            avatarUrl = ch.avatarUrl,
                            transportMode = transportMode,
                            sizeDp = 44,
                            sourceType = ch.sourceType,
                            username = ch.username
                        )
                    }
                    if (unread > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = if (unread > 99) "99+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ch.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        textDirection = TextDirection.Content
                    ),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeedEmptyState(
    hasChannels: Boolean,
    onOpenDiscover: () -> Unit,
    onOpenAddChannel: () -> Unit,
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
                    "Try refreshing your subscribed feeds or clearing your search filter."
                } else {
                    stringResource(R.string.empty_feed_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenDiscover,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_open_discover_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.browse_discover_cta))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
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
        }
    }
}
