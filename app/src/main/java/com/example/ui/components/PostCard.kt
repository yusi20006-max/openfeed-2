package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FeedPost
import com.example.data.model.LinkPreview
import com.example.data.model.MediaAttachment
import com.example.data.model.MediaType
import com.example.data.model.PollData
import com.example.data.model.TransportMode
import com.example.data.network.UrlRewriter
import com.example.ui.MediaViewerState

@Composable
fun PostCard(
    post: FeedPost,
    transportMode: TransportMode,
    autoLoadMedia: Boolean,
    fontScale: Float,
    downloadingKey: String?,
    activeAudioUrl: String?,
    onChannelClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onToggleBookmark: (FeedPost) -> Unit,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit,
    onCopiedToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.uniqueKey}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onChannelClick(post.channelUsername) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelAvatar(
                    title = post.channelTitle,
                    avatarUrl = post.channelAvatarUrl,
                    transportMode = transportMode,
                    sizeDp = 40
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.channelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (post.channelVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = stringResource(R.string.cd_verified_badge),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "@${post.channelUsername}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = post.displayTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!post.viewsText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = post.viewsText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Forwarded-from pill
            if (!post.forwardedFrom.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.forwarded_from, post.forwardedFrom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Reply-to preview
            if (!post.replyToAuthor.isNullOrBlank() || !post.replyToSnippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        if (!post.replyToAuthor.isNullOrBlank()) {
                            Text(
                                text = post.replyToAuthor,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!post.replyToSnippet.isNullOrBlank()) {
                            Text(
                                text = post.replyToSnippet,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDirection = TextDirection.Content
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Media Attachments
            if (post.media.isNotEmpty() && autoLoadMedia) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaBlock(
                    post = post,
                    transportMode = transportMode,
                    downloadingKey = downloadingKey,
                    activeAudioUrl = activeAudioUrl,
                    onOpenMedia = onOpenMedia,
                    onToggleAudio = onToggleAudio,
                    onDownloadMedia = onDownloadMedia
                )
            }

            // Rich Post Text with automatic BiDi direction & clickable @mentions / URLs
            if (post.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                RichPostText(
                    text = post.text,
                    fontScale = fontScale,
                    onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                    onMentionClick = onMentionClick
                )
            }

            // Link Preview
            post.linkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(12.dp))
                LinkPreviewCard(
                    preview = preview,
                    transportMode = transportMode,
                    autoLoadMedia = autoLoadMedia,
                    onClick = { runCatching { uriHandler.openUri(preview.url) } }
                )
            }

            // Poll
            post.poll?.let { poll ->
                Spacer(modifier = Modifier.height(12.dp))
                PollCard(poll = poll)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))

            // Action Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleBookmark(post) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bookmark_button_${post.uniqueKey}")
                    ) {
                        Icon(
                            imageVector = if (post.isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.nav_saved),
                            tint = if (post.isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val toCopy = buildString {
                                if (post.text.isNotBlank()) append(post.text).append("\n\n")
                                append(post.postUrl)
                            }
                            clipboard.setText(AnnotatedString(toCopy))
                            onCopiedToast("Post copied")
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_button_${post.uniqueKey}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.copy_text),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    buildString {
                                        append(post.channelTitle)
                                        append(" (@${post.channelUsername})\n\n")
                                        if (post.text.isNotBlank()) append(post.text).append("\n\n")
                                        append(post.postUrl)
                                    }
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_button_${post.uniqueKey}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.share_post),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = { runCatching { uriHandler.openUri(post.postUrl) } },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("open_original_${post.uniqueKey}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${post.postId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.open_in_browser),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelAvatar(
    title: String,
    avatarUrl: String?,
    transportMode: TransportMode,
    sizeDp: Int = 40
) {
    val resolvedAvatar = avatarUrl?.let { raw ->
        if (transportMode == TransportMode.DIRECT) raw
        else UrlRewriter.toTranslateProxyUrl(raw)
    }

    val initials = remember(title) {
        title.trim()
            .removePrefix("@")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "OF" }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (!resolvedAvatar.isNullOrBlank()) {
            AsyncImage(
                model = resolvedAvatar,
                contentDescription = stringResource(R.string.cd_channel_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RichPostText(
    text: String,
    fontScale: Float,
    onUrlClick: (String) -> Unit,
    onMentionClick: (String) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val urlPattern = remember { Regex("""https?://[^\s<>()]+""") }
    val mentionPattern = remember { Regex("""@[A-Za-z][A-Za-z0-9_]{3,}""") }
    val hashtagPattern = remember { Regex("""#[\p{L}\p{N}_]+""") }

    val annotated = remember(text, primaryColor, secondaryColor) {
        buildAnnotatedString {
            append(text)
            urlPattern.findAll(text).forEach { match ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "url",
                        styles = TextLinkStyles(
                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)
                        )
                    ) {
                        onUrlClick(match.value)
                    },
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
            mentionPattern.findAll(text).forEach { match ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "mention",
                        styles = TextLinkStyles(
                            style = SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)
                        )
                    ) {
                        onMentionClick(match.value)
                    },
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
            hashtagPattern.findAll(text).forEach { match ->
                addStyle(
                    style = SpanStyle(color = secondaryColor, fontWeight = FontWeight.Medium),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    val baseStyle = MaterialTheme.typography.bodyLarge
    Text(
        text = annotated,
        style = baseStyle.copy(
            fontSize = (baseStyle.fontSize.value * fontScale).sp,
            lineHeight = (baseStyle.lineHeight.value * fontScale).sp,
            textDirection = TextDirection.Content
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PostMediaBlock(
    post: FeedPost,
    transportMode: TransportMode,
    downloadingKey: String?,
    activeAudioUrl: String?,
    onOpenMedia: (MediaViewerState) -> Unit,
    onToggleAudio: (String) -> Unit,
    onDownloadMedia: (FeedPost, MediaAttachment) -> Unit
) {
    val photos = post.media.filter { it.type == MediaType.PHOTO }
    val videos = post.media.filter { it.type == MediaType.VIDEO || it.type == MediaType.GIF }
    val audios = post.media.filter { it.type == MediaType.VOICE || it.type == MediaType.AUDIO }
    val docs = post.media.filter { it.type == MediaType.DOCUMENT }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photos.isNotEmpty()) {
            PhotoGrid(
                photos = photos,
                transportMode = transportMode,
                onPhotoClick = { index ->
                    onOpenMedia(MediaViewerState.PhotoGallery(post, photos, index))
                }
            )
        }

        videos.forEach { video ->
            val thumbUrl = UrlRewriter.resolvePreferredUrl(
                directUrl = video.thumbnailUrl,
                bridgeUrl = video.thumbnailBridgeUrl,
                mode = transportMode
            )
            val isDl = downloadingKey == video.url
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onOpenMedia(MediaViewerState.VideoPlayer(post, video)) }
            ) {
                if (!thumbUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = buildString {
                                append(if (video.type == MediaType.GIF) "GIF" else "VIDEO")
                                if (!video.meta.isNullOrBlank()) append(" • ${video.meta}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        onClick = { onDownloadMedia(post, video) },
                        enabled = !isDl,
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDl) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.download_media),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        audios.forEach { audio ->
            InlineAudioCard(
                post = post,
                audio = audio,
                transportMode = transportMode,
                isPlaying = activeAudioUrl == audio.url,
                isDownloading = downloadingKey == audio.url,
                onTogglePlay = { onToggleAudio(audio.url) },
                onDownload = { onDownloadMedia(post, audio) }
            )
        }

        docs.forEach { doc ->
            val isDl = downloadingKey == doc.url
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title ?: "File",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!doc.meta.isNullOrBlank()) {
                            Text(
                                text = doc.meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { onDownloadMedia(post, doc) },
                        enabled = !isDl,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        if (isDl) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.download_media),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<MediaAttachment>,
    transportMode: TransportMode,
    onPhotoClick: (Int) -> Unit
) {
    if (photos.size == 1) {
        val url = UrlRewriter.resolveMediaUrl(photos[0], transportMode)
        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.cd_post_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onPhotoClick(0) }
        )
    } else {
        val visible = photos.take(4)
        val extraCount = photos.size - 4
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.chunked(2).forEachIndexed { rowIdx, rowPhotos ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowPhotos.forEachIndexed { colIdx, photo ->
                        val flatIdx = rowIdx * 2 + colIdx
                        val url = UrlRewriter.resolveMediaUrl(photo, transportMode)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.15f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPhotoClick(flatIdx) }
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = stringResource(R.string.cd_post_image),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (flatIdx == 3 && extraCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+$extraCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    if (rowPhotos.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineAudioCard(
    post: FeedPost,
    audio: MediaAttachment,
    transportMode: TransportMode,
    isPlaying: Boolean,
    isDownloading: Boolean,
    onTogglePlay: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val mediaUrl = UrlRewriter.resolveMediaUrl(audio, transportMode)

    if (isPlaying) {
        val player = remember(mediaUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUrl))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onTogglePlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (audio.type == MediaType.VOICE) Icons.Rounded.GraphicEq else Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audio.title ?: if (audio.type == MediaType.VOICE) "Voice Message" else "Audio",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isPlaying) "Playing stream…" else (audio.meta ?: "Tap play to listen"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.download_media),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(
    preview: LinkPreview,
    transportMode: TransportMode,
    autoLoadMedia: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = preview.siteName ?: preview.url.substringAfter("://").substringBefore("/"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!preview.title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = preview.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            textDirection = TextDirection.Content
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!preview.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preview.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.Content
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (autoLoadMedia && !preview.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                val imgUrl = if (transportMode == TransportMode.DIRECT) preview.imageUrl
                else UrlRewriter.toTranslateProxyUrl(preview.imageUrl)
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun PollCard(poll: PollData) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Poll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = poll.question,
                    style = MaterialTheme.typography.titleSmall.copy(
                        textDirection = TextDirection.Content
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            poll.options.forEach { option ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDirection = TextDirection.Content
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.percentText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { option.percentFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
            if (!poll.totalVotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = poll.totalVotes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
