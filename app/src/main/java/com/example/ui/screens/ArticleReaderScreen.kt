package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ThemeMode
import com.example.data.network.ArticleBlock
import com.example.ui.ArticleReaderUiState
import com.example.ui.theme.resolveIsDarkTheme

@Composable
fun ArticleReaderScreen(
    state: ArticleReaderUiState,
    fontScale: Float,
    themeMode: ThemeMode,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    if (state is ArticleReaderUiState.Closed) return

    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isDark = resolveIsDarkTheme(themeMode)

    val currentUrl = when (state) {
        is ArticleReaderUiState.Loading -> state.url
        is ArticleReaderUiState.Success -> state.article.url
        is ArticleReaderUiState.Error -> state.url
        ArticleReaderUiState.Closed -> ""
    }

    val currentTitle = when (state) {
        is ArticleReaderUiState.Loading -> state.initialTitle
        is ArticleReaderUiState.Success -> state.article.title
        is ArticleReaderUiState.Error -> state.initialTitle
        ArticleReaderUiState.Closed -> ""
    }

    val subtitleInfo = when (state) {
        is ArticleReaderUiState.Success -> buildString {
            state.article.siteName?.let { append(it) }
            if (isNotEmpty()) append(" • ")
            append(stringResource(R.string.reader_minutes, state.article.estimatedReadMinutes))
        }
        is ArticleReaderUiState.Loading -> stringResource(R.string.reader_loading)
        is ArticleReaderUiState.Error -> currentUrl.substringAfter("://").substringBefore("/")
        ArticleReaderUiState.Closed -> ""
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reader Top Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reader_mode_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onFontScaleChange((fontScale - 0.1f).coerceAtLeast(0.85f)) },
                        enabled = fontScale > 0.86f,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_font_decrease")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextDecrease,
                            contentDescription = "Decrease text size"
                        )
                    }

                    IconButton(
                        onClick = { onFontScaleChange((fontScale + 0.1f).coerceAtMost(1.35f)) },
                        enabled = fontScale < 1.34f,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_font_increase")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextIncrease,
                            contentDescription = "Increase text size"
                        )
                    }

                    IconButton(
                        onClick = { onToggleDarkMode(isDark) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_dark_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = stringResource(R.string.dark_mode_toggle)
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    buildString {
                                        if (currentTitle.isNotBlank()) append(currentTitle).append("\n\n")
                                        append(currentUrl)
                                    }
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share article"))
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.share_post)
                        )
                    }

                    IconButton(
                        onClick = { runCatching { uriHandler.openUri(currentUrl) } },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_open_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.open_in_browser)
                        )
                    }
                }
            }

            when (state) {
                is ArticleReaderUiState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 680.dp)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            if (state.initialTitle.isNotBlank()) {
                                Text(
                                    text = state.initialTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        textDirection = TextDirection.Content
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = stringResource(R.string.reader_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is ArticleReaderUiState.Error -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 680.dp)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stringResource(R.string.reader_error_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = onRetry,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("reader_retry_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.reader_retry))
                                        }
                                        OutlinedButton(
                                            onClick = { runCatching { uriHandler.openUri(state.url) } },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("reader_error_open_browser_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.open_in_browser))
                                        }
                                    }
                                }
                            }
                        }
                        if (state.fallbackText.isNotBlank()) {
                            item {
                                FallbackExcerptCard(
                                    title = state.initialTitle,
                                    excerpt = state.fallbackText,
                                    fontScale = fontScale
                                )
                            }
                        }
                    }
                }

                is ArticleReaderUiState.Success -> {
                    val article = state.article
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (!article.leadImageUrl.isNullOrBlank()) {
                                    item(key = "lead_image") {
                                        AsyncImage(
                                            model = article.leadImageUrl,
                                            contentDescription = article.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        )
                                    }
                                }

                                item(key = "article_header") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (!article.siteName.isNullOrBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = article.siteName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Schedule,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.reader_minutes, article.estimatedReadMinutes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!article.publishedTime.isNullOrBlank()) {
                                                Text(
                                                    text = "• ${article.publishedTime}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text(
                                            text = article.title,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (22f * fontScale).sp,
                                                lineHeight = (30f * fontScale).sp,
                                                textDirection = TextDirection.Content
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )

                                        if (!article.byline.isNullOrBlank()) {
                                            Text(
                                                text = article.byline,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    textDirection = TextDirection.Content
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 6.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }

                                if (article.blocks.isEmpty()) {
                                    if (state.fallbackText.isNotBlank()) {
                                        item(key = "fallback_summary") {
                                            FallbackExcerptCard(
                                                title = "",
                                                excerpt = state.fallbackText,
                                                fontScale = fontScale
                                            )
                                        }
                                    }
                                    item(key = "empty_full_notice") {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                    text = stringResource(R.string.reader_empty_body),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = { runCatching { uriHandler.openUri(article.url) } },
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(stringResource(R.string.open_in_browser))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(article.blocks) { block ->
                                        ReaderBlockItem(block = block, fontScale = fontScale)
                                    }

                                    item(key = "article_footer") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { runCatching { uriHandler.openUri(article.url) } },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.open_in_browser))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ArticleReaderUiState.Closed -> Unit
            }
        }
    }
}

@Composable
private fun ReaderBlockItem(
    block: ArticleBlock,
    fontScale: Float
) {
    when (block) {
        is ArticleBlock.Heading -> {
            val baseSize = if (block.level <= 2) 19f else 17f
            Text(
                text = block.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (baseSize * fontScale).sp,
                    lineHeight = ((baseSize + 7f) * fontScale).sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        is ArticleBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (16.5f * fontScale).sp,
                    lineHeight = (27f * fontScale).sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
            )
        }

        is ArticleBlock.Quote -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = (15.5f * fontScale).sp,
                            lineHeight = (24f * fontScale).sp,
                            textDirection = TextDirection.Content
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is ArticleBlock.CodeBlock -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (13f * fontScale).sp,
                        lineHeight = (19f * fontScale).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FallbackExcerptCard(
    title: String,
    excerpt: String,
    fontScale: Float
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reader_summary_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDirection = TextDirection.Content
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (16f * fontScale).sp,
                    lineHeight = (25f * fontScale).sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
