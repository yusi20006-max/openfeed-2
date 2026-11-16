package com.example.data.model

enum class FeedSourceType {
    TELEGRAM,
    RSS
}

enum class TransportMode(val label: String, val subtitle: String) {
    AUTO("Smart Auto", "Tries Google Translate Bridge or Direct automatically"),
    TRANSLATE_BRIDGE("Translate Bridge", "Routes via *.translate.goog (best for filtered networks)"),
    DIRECT("Direct (t.me)", "Connects directly to t.me/s/ (best with VPN or open net)"),
    CUSTOM_MIRROR("Custom Mirror", "Uses your own OpenFeed / Cloudflare Worker relay URL")
}

enum class BridgeLangProfile(val label: String, val sl: String, val tl: String) {
    PRESERVE_ORIGINAL("Keep Original Text", "la", "en"),
    TRANSLATE_FA("Translate to فارسی", "auto", "fa"),
    TRANSLATE_EN("Translate to English", "auto", "en")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class DohProvider(val label: String, val endpoint: String, val bootstrapIp: String) {
    GOOGLE("Google DoH (8.8.8.8)", "https://dns.google/resolve", "8.8.8.8"),
    CLOUDFLARE("Cloudflare DoH (1.1.1.1)", "https://cloudflare-dns.com/dns-query", "1.1.1.1")
}

enum class OfflinePrefetchPolicy(val label: String) {
    WIFI_ONLY("Wi-Fi Only"),
    ALWAYS("Always"),
    MANUAL("Manual Only")
}

data class OfflineVaultStats(
    val photoCount: Int = 0,
    val totalBytes: Long = 0L,
    val cachedArticleCount: Int = 0
)

data class OfflinePrepProgress(
    val completed: Int,
    val total: Int
)

enum class MediaType {
    PHOTO,
    VIDEO,
    GIF,
    VOICE,
    AUDIO,
    DOCUMENT
}

data class MediaAttachment(
    val type: MediaType,
    val url: String,
    val bridgeUrl: String,
    val thumbnailUrl: String? = null,
    val thumbnailBridgeUrl: String? = null,
    val title: String? = null,
    val meta: String? = null
)

data class LinkPreview(
    val siteName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String,
    val imageUrl: String? = null
)

data class PollOption(
    val text: String,
    val percentText: String,
    val percentFloat: Float
)

data class PollData(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: String? = null
)

data class FeedPost(
    val uniqueKey: String,
    val channelUsername: String,
    val channelTitle: String,
    val channelAvatarUrl: String?,
    val channelVerified: Boolean,
    val sourceType: FeedSourceType = FeedSourceType.TELEGRAM,
    val postId: Long,
    val postUrl: String,
    val timestampIso: String,
    val timestampEpoch: Long,
    val displayTime: String,
    val viewsText: String?,
    val text: String,
    val links: List<String>,
    val mentions: List<String>,
    val hashtags: List<String>,
    val forwardedFrom: String?,
    val replyToAuthor: String?,
    val replyToSnippet: String?,
    val media: List<MediaAttachment>,
    val linkPreview: LinkPreview?,
    val poll: PollData?,
    val isBookmarked: Boolean,
    val isRead: Boolean = false,
    val fetchedAt: Long
)

data class ChannelInfo(
    val username: String,
    val title: String,
    val description: String,
    val avatarUrl: String?,
    val subscribersText: String?,
    val isVerified: Boolean,
    val isFavorite: Boolean,
    val sourceType: FeedSourceType = FeedSourceType.TELEGRAM,
    val feedUrl: String? = null,
    val lastFetchedAt: Long,
    val lastTransportUsed: String?,
    val lastLatencyMs: Long?
)

data class SuggestedChannel(
    val username: String,
    val title: String,
    val category: String,
    val description: String,
    val sourceType: FeedSourceType = FeedSourceType.TELEGRAM,
    val feedUrl: String? = null,
    val logoUrl: String? = null
)

data class UserSettings(
    val transportMode: TransportMode = TransportMode.AUTO,
    val bridgeLangProfile: BridgeLangProfile = BridgeLangProfile.PRESERVE_ORIGINAL,
    val useDoH: Boolean = true,
    val dohProvider: DohProvider = DohProvider.GOOGLE,
    val customMirrorUrl: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val autoLoadMedia: Boolean = true,
    val fontScale: Float = 1.0f,
    val backgroundSyncEnabled: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val notifyFavoritesOnly: Boolean = true,
    val lastBackgroundSyncAt: Long = 0L,
    val offlinePrefetchPolicy: OfflinePrefetchPolicy = OfflinePrefetchPolicy.WIFI_ONLY,
    val offlineMaxCacheMb: Int = 150,
    val offlineCacheFullRssArticles: Boolean = true
)

data class DeepLinkTarget(
    val username: String,
    val postId: Long? = null
)

data class ConnectionDiagnostics(
    val isRunning: Boolean = false,
    val dohReachable: Boolean? = null,
    val dohLatencyMs: Long? = null,
    val dohResolvedIp: String? = null,
    val bridgeReachable: Boolean? = null,
    val bridgeLatencyMs: Long? = null,
    val directReachable: Boolean? = null,
    val directLatencyMs: Long? = null,
    val summary: String = ""
)
