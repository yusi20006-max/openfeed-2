package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.example.data.model.FeedPost
import com.example.data.model.MediaType
import com.example.data.model.OfflinePrefetchPolicy
import com.example.data.model.TransportMode
import com.example.data.model.UserSettings
import com.example.data.network.UrlRewriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class VaultDiskStats(
    val photoCount: Int = 0,
    val totalBytes: Long = 0L
)

data class OfflineImageCandidate(
    val primaryUrl: String,
    val fallbackUrl: String? = null
)

class OfflineMediaVault(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
    }

    private val _diskStatsFlow = MutableStateFlow(computeStats())
    val diskStatsFlow: StateFlow<VaultDiskStats> = _diskStatsFlow.asStateFlow()

    fun refreshStats() {
        _diskStatsFlow.value = computeStats()
    }

    fun resolveLocalFile(rawUrl: String): File? {
        if (rawUrl.isBlank()) return null
        val file = fileForUrl(rawUrl)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun isCached(rawUrl: String): Boolean = resolveLocalFile(rawUrl) != null

    fun isWifiConnected(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
    }

    fun shouldAutoPrefetch(policy: OfflinePrefetchPolicy): Boolean = when (policy) {
        OfflinePrefetchPolicy.ALWAYS -> true
        OfflinePrefetchPolicy.WIFI_ONLY -> isWifiConnected()
        OfflinePrefetchPolicy.MANUAL -> false
    }

    suspend fun cacheImage(
        httpClient: OkHttpClient,
        primaryUrl: String,
        fallbackUrl: String? = null,
        settings: UserSettings = UserSettings()
    ): Boolean = withContext(Dispatchers.IO) {
        val canonical = canonicalUrl(primaryUrl)
        if (!canonical.startsWith("http://") && !canonical.startsWith("https://")) {
            return@withContext false
        }

        val targetFile = fileForCanonical(canonical)
        if (targetFile.isFile && targetFile.length() > 0L) {
            targetFile.setLastModified(System.currentTimeMillis())
            return@withContext true
        }

        val candidateUrls = buildCandidateUrls(canonical, primaryUrl, fallbackUrl, settings)
        for (url in candidateUrls) {
            val ok = downloadToVault(httpClient, url, targetFile)
            if (ok) {
                refreshStats()
                return@withContext true
            }
        }
        false
    }

    suspend fun trimToMaxBytes(maxBytes: Long, pinnedUrls: Set<String>) = withContext(Dispatchers.IO) {
        val pinnedKeys = pinnedUrls.mapNotNull { url ->
            canonicalUrl(url).takeIf { it.isNotBlank() }?.let { sha256Hex(it) }
        }.toSet()

        val files = vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".img") }?.toList().orEmpty()
        var currentTotal = files.sumOf { it.length() }
        if (currentTotal <= maxBytes) {
            refreshStats()
            return@withContext
        }

        val evictable = files
            .filter { file -> file.name.removeSuffix(".img") !in pinnedKeys }
            .sortedBy { it.lastModified() }

        for (file in evictable) {
            if (currentTotal <= maxBytes) break
            val size = file.length()
            if (file.delete()) {
                currentTotal -= size
            }
        }
        refreshStats()
    }

    suspend fun clearNonPinned(pinnedUrls: Set<String>) = withContext(Dispatchers.IO) {
        val pinnedKeys = pinnedUrls.mapNotNull { url ->
            canonicalUrl(url).takeIf { it.isNotBlank() }?.let { sha256Hex(it) }
        }.toSet()

        val files = vaultDir.listFiles()?.toList().orEmpty()
        for (file in files) {
            if (!file.isFile) continue
            if (file.name.endsWith(".tmp")) {
                file.delete()
                continue
            }
            if (file.name.endsWith(".img")) {
                val hash = file.name.removeSuffix(".img")
                if (hash !in pinnedKeys) {
                    file.delete()
                }
            }
        }
        refreshStats()
    }

    private fun downloadToVault(httpClient: OkHttpClient, url: String, targetFile: File): Boolean {
        val tmpFile = File(vaultDir, "${targetFile.name}.${System.nanoTime()}.tmp")
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.startsWith("text/html") ||
                    contentType.startsWith("video/") ||
                    contentType.startsWith("audio/") ||
                    contentType.contains("application/json")
                ) {
                    return false
                }
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (contentLength > MAX_SINGLE_PHOTO_BYTES) return false

                val body = response.body ?: return false
                var totalRead = 0L
                val buffer = ByteArray(8192)
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalRead += read
                            if (totalRead > MAX_SINGLE_PHOTO_BYTES) {
                                return false
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (totalRead < 64L) return false
                if (targetFile.exists()) targetFile.delete()
                val moved = tmpFile.renameTo(targetFile)
                if (!moved) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
                targetFile.setLastModified(System.currentTimeMillis())
                true
            }
        }.getOrElse {
            tmpFile.delete()
            false
        }
    }

    private fun buildCandidateUrls(
        canonical: String,
        primaryUrl: String,
        fallbackUrl: String?,
        settings: UserSettings
    ): List<String> {
        val bridgeCandidate = fallbackUrl?.takeIf { it.isNotBlank() }
            ?: if (UrlRewriter.isTelegramOwnedHost(runCatching { android.net.Uri.parse(canonical).host }.getOrNull())) {
                UrlRewriter.toTranslateProxyUrl(canonical, settings.bridgeLangProfile)
            } else {
                null
            }
        val ordered = when (settings.transportMode) {
            TransportMode.DIRECT -> listOfNotNull(canonical, primaryUrl, bridgeCandidate)
            else -> listOfNotNull(bridgeCandidate, primaryUrl, canonical)
        }
        return ordered
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    private fun fileForUrl(rawUrl: String): File = fileForCanonical(canonicalUrl(rawUrl))

    private fun fileForCanonical(canonical: String): File =
        File(vaultDir, "${sha256Hex(canonical)}.img")

    private fun computeStats(): VaultDiskStats {
        if (!vaultDir.exists()) return VaultDiskStats()
        val files = vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".img") } ?: return VaultDiskStats()
        var total = 0L
        for (file in files) {
            total += file.length()
        }
        return VaultDiskStats(photoCount = files.size, totalBytes = total)
    }

    companion object {
        private const val VAULT_DIR_NAME = "offline_photos"
        private const val MAX_SINGLE_PHOTO_BYTES = 8L * 1024L * 1024L

        fun canonicalUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty()) return ""
            return UrlRewriter.toDirectTelegramUrl(trimmed)
        }

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Collects only photos, link-preview thumbnails, and channel avatars from posts.
         * Strictly excludes VIDEO, GIF, VOICE, AUDIO, and DOCUMENT attachments.
         */
        fun extractPhotoCandidates(posts: List<FeedPost>): List<OfflineImageCandidate> {
            val seen = LinkedHashMap<String, OfflineImageCandidate>()

            fun addCandidate(primary: String?, fallback: String? = null) {
                val clean = primary?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: return
                val key = canonicalUrl(clean)
                if (!seen.containsKey(key)) {
                    seen[key] = OfflineImageCandidate(
                        primaryUrl = clean,
                        fallbackUrl = fallback?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    )
                }
            }

            for (post in posts) {
                addCandidate(post.channelAvatarUrl)
                for (attachment in post.media) {
                    if (attachment.type == MediaType.PHOTO) {
                        addCandidate(attachment.url, attachment.bridgeUrl)
                    }
                }
                addCandidate(post.linkPreview?.imageUrl)
            }
            return seen.values.toList()
        }
    }
}

class OfflineVaultCoilInterceptor(
    private val vault: OfflineMediaVault
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && (data.startsWith("http://") || data.startsWith("https://"))) {
            val localFile = vault.resolveLocalFile(data)
            if (localFile != null) {
                val localRequest = chain.request.newBuilder()
                    .data(localFile)
                    .build()
                return chain.proceed(localRequest)
            }
        }
        return chain.proceed(chain.request)
    }
}
