package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FeedPost
import com.example.data.model.FeedSourceType
import com.example.data.model.MediaAttachment
import com.example.ui.MediaViewerState
import com.example.ui.OpenFeedViewModel
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    username: String,
    targetPostId: Long? = null,
    viewModel: OpenFeedViewModel,
    onBack: () -> Unit,
    onOpenChannelDetail: (String) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val channel by viewModel.observeChannel(username).collectAsStateWithLifecycle(initialValue = null)
    val posts by viewModel.observeChannelPosts(username).collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoadingOlder by viewModel.isChannelLoadingOlder(username).collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var hasScrolledToTarget by remember(username, targetPostId) { mutableStateOf(false) }

    LaunchedEffect(username) {
        if (channel == null) {
            viewModel.addChannel(username)
        }
    }

    LaunchedEffect(targetPostId, posts) {
        val desired = targetPostId
        if (desired != null && desired > 0L && !hasScrolledToTarget) {
            val idx = posts.indexOfFirst { it.postId == desired }
            if (idx >= 0) {
                listState.animateScrollToItem(idx + 1)
                hasScrolledToTarget = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("channel_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                val isRss = channel?.sourceType == FeedSourceType.RSS || username.startsWith("rss_")
                val subtitleLabel = if (isRss) {
                    channel?.subscribersText ?: "RSS Feed"
                } else {
                    "@$username"
                }
                val externalUrl = channel?.feedUrl?.takeIf { it.isNotBlank() } ?: "https://t.me/s/$username"

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel?.title ?: subtitleLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.Content
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRss) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if ((uiState.unreadCounts[username] ?: 0) > 0) {
                    IconButton(
                        onClick = { viewModel.markChannelRead(username) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("channel_detail_mark_read_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = stringResource(R.string.mark_all_read),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (channel != null) {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(username, channel!!.isFavorite) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("channel_detail_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (channel!!.isFavorite) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (channel!!.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.refreshSingleChannel(username) },
                    enabled = !uiState.isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("channel_detail_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.refresh_feed)
                    )
                }
                IconButton(
                    onClick = { runCatching { uriHandler.openUri(externalUrl) } },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.open_in_browser)
                    )
                }
            }
        }

        if (uiState.isRefreshing || uiState.isAddingChannel) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshSingleChannel(username) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
            // Channel Info Header Card
            item(key = "header_$username") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(
                                title = channel?.title ?: username,
                                avatarUrl = channel?.avatarUrl,
                                transportMode = uiState.settings.transportMode,
                                sizeDp = 56,
                                sourceType = channel?.sourceType ?: FeedSourceType.TELEGRAM,
                                username = username
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel?.title ?: "@$username",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            textDirection = TextDirection.Content
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (channel?.isVerified == true) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = stringResource(R.string.cd_verified_badge),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    channel?.subscribersText?.let { subs ->
                                        val isRss = channel?.sourceType == FeedSourceType.RSS
                                        Text(
                                            text = if (isRss) subs else stringResource(R.string.subscribers_label, subs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    channel?.lastTransportUsed?.let { transport ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "$transport • ${channel?.lastLatencyMs ?: 0}ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!channel?.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = channel?.description.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = TextDirection.Content
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (posts.isEmpty() && !uiState.isAddingChannel) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.addChannelError ?: "No posts loaded yet. Tap refresh above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(posts, key = { it.uniqueKey }) { post ->
                    PostCard(
                        post = post,
                        transportMode = uiState.settings.transportMode,
                        autoLoadMedia = uiState.settings.autoLoadMedia,
                        fontScale = uiState.settings.fontScale,
                        downloadingKey = uiState.downloadingKey,
                        activeAudioUrl = uiState.activeAudioUrl,
                        onChannelClick = {},
                        onMentionClick = { mention ->
                            val target = mention.removePrefix("@")
                            if (!target.equals(username, ignoreCase = true)) {
                                onOpenChannelDetail(target)
                            }
                        },
                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                        onOpenMedia = onOpenMedia,
                        onToggleAudio = { viewModel.toggleAudioPlayback(it) },
                        onDownloadMedia = onDownloadMedia,
                        onCopiedToast = onShowMessage,
                        httpClient = viewModel.exoHttpClient,
                        isHighlighted = (targetPostId != null && post.postId == targetPostId),
                        onPostInteracted = { viewModel.markPostRead(it.uniqueKey) },
                        onOpenArticle = { p, url, title ->
                            viewModel.openArticleReader(
                                url = url,
                                title = title,
                                fallbackText = p.text,
                                postUniqueKey = p.uniqueKey
                            )
                        }
                    )
                }

                if (channel?.sourceType != FeedSourceType.RSS && !username.startsWith("rss_")) {
                    item(key = "load_older_$username") {
                        OutlinedButton(
                            onClick = { viewModel.loadOlderPosts(username, posts) },
                            enabled = !isLoadingOlder,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("load_older_posts_button")
                        ) {
                            if (isLoadingOlder) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading older posts…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.load_older))
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }
}
