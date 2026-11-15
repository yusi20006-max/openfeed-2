package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.ConnectionDiagnostics
import com.example.data.model.TransportMode
import com.example.data.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DownloadResult(
    val uri: Uri,
    val displayPath: String,
    val bytesWritten: Long
)

class OpenFeedNetworkClient(
    private val settingsProvider: () -> UserSettings
) {
    val dohDns = DoHDns(
        isEnabledProvider = { settingsProvider().useDoH },
        dohProviderGetter = { settingsProvider().dohProvider }
    )

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(dohDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", "en-US,en;q=0.9,fa;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()

    private val fastDirectClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(4500, TimeUnit.MILLISECONDS)
        .readTimeout(6000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun fetchChannelPage(
        username: String,
        beforeId: Long? = null,
        existingIsFavorite: Boolean = false
    ): ParsedChannelPage = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        when (settings.transportMode) {
            TransportMode.AUTO -> fetchAutoWithFallback(username, beforeId, settings, existingIsFavorite)
            TransportMode.TRANSLATE_BRIDGE -> fetchViaTranslateBridge(username, beforeId, settings, existingIsFavorite)
            TransportMode.DIRECT -> fetchViaDirect(username, beforeId, settings, existingIsFavorite)
            TransportMode.CUSTOM_MIRROR -> fetchViaCustomMirror(username, beforeId, settings, existingIsFavorite)
        }
    }

    private fun fetchAutoWithFallback(
        username: String,
        beforeId: Long?,
        settings: UserSettings,
        existingIsFavorite: Boolean
    ): ParsedChannelPage {
        // Try Google Translate Bridge first if Direct is typically filtered, or try Direct fast then Bridge.
        // In Openfeed, Google Translate bridge is the primary censorship bypass, with Direct as fast peer.
        val bridgeError: Throwable? = try {
            return fetchViaTranslateBridge(username, beforeId, settings, existingIsFavorite)
        } catch (t: Throwable) {
            t
        }

        return try {
            fetchViaDirect(username, beforeId, settings, existingIsFavorite)
        } catch (directError: Throwable) {
            throw IOException(
                "Bridge failed (${bridgeError?.message}) & Direct failed (${directError.message})"
            )
        }
    }

    private fun fetchViaTranslateBridge(
        username: String,
        beforeId: Long?,
        settings: UserSettings,
        existingIsFavorite: Boolean
    ): ParsedChannelPage {
        val url = UrlRewriter.buildTranslateBridgeChannelUrl(
            username = username,
            beforeId = beforeId,
            profile = settings.bridgeLangProfile
        )
        val start = System.currentTimeMillis()
        val html = executeGetString(httpClient, url)
        val latency = System.currentTimeMillis() - start

        val label = if (settings.useDoH) "Bridge • DoH" else "Translate Bridge"
        val parsed = TelegramHtmlParser.parse(
            html = html,
            username = username,
            transportUsed = label,
            latencyMs = latency,
            bridgeProfile = settings.bridgeLangProfile,
            existingIsFavorite = existingIsFavorite
        )
        if (parsed.posts.isEmpty() && parsed.channel.title == "@$username" && parsed.channel.description.isBlank()) {
            throw IOException("Channel @$username not found or empty preview")
        }
        return parsed
    }

    private fun fetchViaDirect(
        username: String,
        beforeId: Long?,
        settings: UserSettings,
        existingIsFavorite: Boolean
    ): ParsedChannelPage {
        val url = UrlRewriter.buildDirectChannelUrl(username, beforeId)
        val start = System.currentTimeMillis()
        val html = executeGetString(fastDirectClient, url)
        val latency = System.currentTimeMillis() - start

        val label = if (settings.useDoH) "Direct • DoH" else "Direct"
        val parsed = TelegramHtmlParser.parse(
            html = html,
            username = username,
            transportUsed = label,
            latencyMs = latency,
            bridgeProfile = settings.bridgeLangProfile,
            existingIsFavorite = existingIsFavorite
        )
        if (parsed.posts.isEmpty() && parsed.channel.title == "@$username" && parsed.channel.description.isBlank()) {
            throw IOException("Channel @$username not found or unavailable")
        }
        return parsed
    }

    private fun fetchViaCustomMirror(
        username: String,
        beforeId: Long?,
        settings: UserSettings,
        existingIsFavorite: Boolean
    ): ParsedChannelPage {
        val url = UrlRewriter.buildCustomMirrorUrl(settings.customMirrorUrl, username, beforeId)
            ?: throw IOException("Custom mirror URL is empty. Configure it in Settings.")
        val start = System.currentTimeMillis()
        val html = executeGetString(httpClient, url)
        val latency = System.currentTimeMillis() - start

        return TelegramHtmlParser.parse(
            html = html,
            username = username,
            transportUsed = "Custom Mirror",
            latencyMs = latency,
            bridgeProfile = settings.bridgeLangProfile,
            existingIsFavorite = existingIsFavorite
        )
    }

    private fun executeGetString(client: OkHttpClient, url: String): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${request.url.host}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body")
            if (body.length < 120) {
                throw IOException("Incomplete HTML response")
            }
            return body
        }
    }

    suspend fun runDiagnostics(): ConnectionDiagnostics = withContext(Dispatchers.IO) {
        val settings = settingsProvider()

        var dohOk = false
        var dohLatency: Long? = null
        var dohIp: String? = null
        runCatching {
            val (ips, ms) = dohDns.resolveDirectCheck("t-me.translate.goog", settings.dohProvider)
            dohOk = ips.isNotEmpty()
            dohLatency = ms
            dohIp = ips.firstOrNull()
        }

        var bridgeOk = false
        var bridgeLatency: Long? = null
        runCatching {
            val start = System.currentTimeMillis()
            val url = UrlRewriter.buildTranslateBridgeChannelUrl("telegram", null, settings.bridgeLangProfile)
            val body = executeGetString(httpClient, url)
            bridgeLatency = System.currentTimeMillis() - start
            bridgeOk = body.contains("tgme_widget_message") || body.contains("tgme_channel_info")
        }

        var directOk = false
        var directLatency: Long? = null
        runCatching {
            val start = System.currentTimeMillis()
            val url = UrlRewriter.buildDirectChannelUrl("telegram")
            val body = executeGetString(fastDirectClient, url)
            directLatency = System.currentTimeMillis() - start
            directOk = body.contains("tgme_widget_message") || body.contains("tgme_channel_info")
        }

        val summary = when {
            bridgeOk && directOk -> "All routes operational (Bridge + Direct + DoH)"
            bridgeOk -> "Filtered network bypassed via Translate Bridge + DoH"
            directOk -> "Direct t.me reachable (Bridge unavailable)"
            else -> "No route succeeded. Check internet connection or Custom Mirror."
        }

        ConnectionDiagnostics(
            isRunning = false,
            dohReachable = dohOk,
            dohLatencyMs = dohLatency,
            dohResolvedIp = dohIp,
            bridgeReachable = bridgeOk,
            bridgeLatencyMs = bridgeLatency,
            directReachable = directOk,
            directLatencyMs = directLatency,
            summary = summary
        )
    }

    suspend fun downloadMedia(
        context: Context,
        directUrl: String,
        bridgeUrl: String,
        fileName: String,
        mimeType: String
    ): DownloadResult = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val orderedUrls = when (settings.transportMode) {
            TransportMode.DIRECT -> listOf(directUrl, bridgeUrl)
            else -> listOf(bridgeUrl, directUrl)
        }.filter { it.isNotBlank() }.distinct()

        var lastError: Throwable? = null
        for (targetUrl in orderedUrls) {
            try {
                val req = Request.Builder().url(targetUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Empty media body")
                    val bytes = body.bytes()
                    return@withContext saveBytesToDownloads(context, bytes, fileName, mimeType)
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IOException("Download failed: ${lastError?.message ?: "Unknown error"}")
    }

    private fun saveBytesToDownloads(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): DownloadResult {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/OpenFeed")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create MediaStore record")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw IOException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return DownloadResult(
                uri = uri,
                displayPath = "Downloads/OpenFeed/$safeName",
                bytesWritten = bytes.size.toLong()
            )
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "OpenFeed"
            ).apply { mkdirs() }
            val outFile = File(dir, safeName)
            FileOutputStream(outFile).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            return DownloadResult(
                uri = uri,
                displayPath = outFile.absolutePath,
                bytesWritten = bytes.size.toLong()
            )
        }
    }
}
