package com.example.data.network

import android.content.Intent
import android.net.Uri
import com.example.data.model.DeepLinkTarget

sealed interface DeepLinkResult {
    data class OpenChannel(val target: DeepLinkTarget) : DeepLinkResult
    data object PrivateInviteUnsupported : DeepLinkResult
    data object Ignored : DeepLinkResult
}

object DeepLinkParser {

    fun parseIntent(intent: Intent?): DeepLinkResult {
        if (intent == null) return DeepLinkResult.Ignored

        val extraChannel = intent.getStringExtra(EXTRA_CHANNEL_USERNAME)?.trim()
        if (!extraChannel.isNullOrBlank()) {
            val resolvedKey = if (extraChannel.startsWith("rss_")) {
                extraChannel
            } else {
                UrlRewriter.normalizeUsername(extraChannel)
            }
            if (resolvedKey != null) {
                val postId = intent.getLongExtra(EXTRA_POST_ID, -1L).takeIf { it > 0 }
                return DeepLinkResult.OpenChannel(DeepLinkTarget(resolvedKey, postId))
            }
        }

        if (intent.action != Intent.ACTION_VIEW) return DeepLinkResult.Ignored
        val uri = intent.data ?: return DeepLinkResult.Ignored
        return parseUri(uri)
    }

    fun parseUri(uri: Uri): DeepLinkResult {
        val scheme = uri.scheme?.lowercase() ?: return DeepLinkResult.Ignored
        val host = uri.host?.lowercase() ?: return DeepLinkResult.Ignored

        if (scheme == "tg" && host == "resolve") {
            val domain = uri.getQueryParameter("domain")?.trim().orEmpty()
            val normalized = UrlRewriter.normalizeUsername(domain) ?: return DeepLinkResult.Ignored
            val postId = uri.getQueryParameter("post")?.toLongOrNull()
            return DeepLinkResult.OpenChannel(DeepLinkTarget(normalized, postId))
        }

        if (scheme in setOf("http", "https") &&
            host in setOf("t.me", "www.t.me", "telegram.me", "www.telegram.me", "t-me.translate.goog")
        ) {
            val segments = uri.pathSegments.filter { it.isNotBlank() }
            if (segments.isEmpty()) return DeepLinkResult.Ignored

            val first = segments[0]
            if (first.startsWith("+") || first.equals("joinchat", ignoreCase = true)) {
                return DeepLinkResult.PrivateInviteUnsupported
            }

            return if (first.equals("s", ignoreCase = true) && segments.size >= 2) {
                val normalized = UrlRewriter.normalizeUsername(segments[1]) ?: return DeepLinkResult.Ignored
                val postId = segments.getOrNull(2)?.toLongOrNull()
                DeepLinkResult.OpenChannel(DeepLinkTarget(normalized, postId))
            } else {
                val normalized = UrlRewriter.normalizeUsername(first) ?: return DeepLinkResult.Ignored
                val postId = segments.getOrNull(1)?.toLongOrNull()
                DeepLinkResult.OpenChannel(DeepLinkTarget(normalized, postId))
            }
        }

        return DeepLinkResult.Ignored
    }

    const val EXTRA_CHANNEL_USERNAME = "extra_channel_username"
    const val EXTRA_POST_ID = "extra_post_id"
}
