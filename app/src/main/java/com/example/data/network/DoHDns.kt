package com.example.data.network

import com.example.data.model.DohProvider
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DoHDns(
    private val isEnabledProvider: () -> Boolean,
    private val dohProviderGetter: () -> DohProvider
) : Dns {

    private data class CachedDnsEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedDnsEntry>()

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = when (hostname) {
                    "dns.google" -> listOf(
                        InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 8, 8)),
                        InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 4, 4))
                    )
                    "cloudflare-dns.com" -> listOf(
                        InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 1, 1, 1)),
                        InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 0, 0, 1))
                    )
                    else -> Dns.SYSTEM.lookup(hostname)
                }
            })
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isEnabledProvider()) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        cache[hostname]?.let { entry ->
            if (entry.expiresAtMs > now && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        val dohResult = runCatching { resolveViaDoH(hostname, dohProviderGetter()) }.getOrNull()
        if (!dohResult.isNullOrEmpty()) {
            cache[hostname] = CachedDnsEntry(dohResult, now + 300_000L)
            return dohResult
        }

        return Dns.SYSTEM.lookup(hostname)
    }

    fun resolveDirectCheck(hostname: String, provider: DohProvider): Pair<List<String>, Long> {
        val start = System.currentTimeMillis()
        val addresses = resolveViaDoH(hostname, provider)
        val latency = System.currentTimeMillis() - start
        if (addresses.isEmpty()) {
            throw UnknownHostException("DoH returned no A records for $hostname")
        }
        return addresses.mapNotNull { it.hostAddress } to latency
    }

    private fun resolveViaDoH(hostname: String, provider: DohProvider): List<InetAddress> {
        val url = provider.endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("name", hostname)
            .addQueryParameter("type", "A")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .header("User-Agent", "OpenFeed-Android/1.0")
            .get()
            .build()

        bootstrapClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val bodyStr = response.body?.string() ?: return emptyList()
            val json = JSONObject(bodyStr)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            val resolved = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val item = answers.getJSONObject(i)
                val type = item.optInt("type", -1)
                if (type == 1) { // A record
                    val ip = item.optString("data", "").trim()
                    if (ip.isNotEmpty()) {
                        runCatching { InetAddress.getByName(ip) }.getOrNull()?.let { resolved.add(it) }
                    }
                }
            }
            return resolved
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
