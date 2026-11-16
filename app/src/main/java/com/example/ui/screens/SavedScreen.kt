package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.ui.FeedUiState
import com.example.ui.MediaViewerState
import com.example.ui.components.PostCard
import okhttp3.OkHttpClient

@Composable
fun SavedScreen(
    uiState: FeedUiState,
    onOpenChannelDetail: (String) -> Unit,
    onToggleBookmark: (FeedPost) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    httpClient: OkHttpClient? = null,
    onMarkPostRead: (String) -> Unit = {},
    onOpenArticle: ((FeedPost, String, String) -> Unit)? = null,
    onPrepareOfflineNow: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nav_saved),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${uiState.bookmarkedPosts.size} bookmarked • ${uiState.offlineVaultStats.photoCount} photos offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val prep = uiState.offlinePrepProgress
                FilledTonalButton(
                    onClick = onPrepareOfflineNow,
                    enabled = prep == null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("saved_prepare_offline_button")
                ) {
                    if (prep != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${prep.completed}/${prep.total}")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.saved_sync_offline))
                    }
                }
            }
        }

        if (uiState.bookmarkedPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 480.dp)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.saved_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.saved_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(uiState.bookmarkedPosts, key = { it.uniqueKey }) { post ->
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
