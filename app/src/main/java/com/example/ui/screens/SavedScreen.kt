package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedPost
import com.example.data.model.MediaAttachment
import com.example.ui.FeedUiState
import com.example.ui.MediaViewerState
import com.example.ui.components.PostCard

@Composable
fun SavedScreen(
    uiState: FeedUiState,
    onOpenChannelDetail: (String) -> Unit,
    onToggleBookmark: (FeedPost) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.nav_saved),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiState.bookmarkedPosts.size} bookmarked for offline reading",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        onCopiedToast = onShowMessage
                    )
                }
            }
        }
    }
}
