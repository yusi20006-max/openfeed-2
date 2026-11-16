package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BridgeLangProfile
import com.example.data.model.DohProvider
import com.example.data.model.OfflinePrefetchPolicy
import com.example.data.model.ThemeMode
import com.example.data.model.TransportMode
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openfeed_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        val BRIDGE_LANG_PROFILE = stringPreferencesKey("bridge_lang_profile")
        val USE_DOH = booleanPreferencesKey("use_doh")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val CUSTOM_MIRROR_URL = stringPreferencesKey("custom_mirror_url")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_LOAD_MEDIA = booleanPreferencesKey("auto_load_media")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val NOTIFY_FAVORITES_ONLY = booleanPreferencesKey("notify_favorites_only")
        val LAST_BACKGROUND_SYNC_AT = longPreferencesKey("last_background_sync_at")
        val OFFLINE_PREFETCH_POLICY = stringPreferencesKey("offline_prefetch_policy")
        val OFFLINE_MAX_CACHE_MB = intPreferencesKey("offline_max_cache_mb")
        val OFFLINE_CACHE_RSS_ARTICLES = booleanPreferencesKey("offline_cache_rss_articles")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            transportMode = prefs[Keys.TRANSPORT_MODE]?.let { name ->
                TransportMode.entries.firstOrNull { it.name == name }
            } ?: TransportMode.AUTO,
            bridgeLangProfile = prefs[Keys.BRIDGE_LANG_PROFILE]?.let { name ->
                BridgeLangProfile.entries.firstOrNull { it.name == name }
            } ?: BridgeLangProfile.PRESERVE_ORIGINAL,
            useDoH = prefs[Keys.USE_DOH] ?: true,
            dohProvider = prefs[Keys.DOH_PROVIDER]?.let { name ->
                DohProvider.entries.firstOrNull { it.name == name }
            } ?: DohProvider.GOOGLE,
            customMirrorUrl = prefs[Keys.CUSTOM_MIRROR_URL] ?: "",
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.DARK,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            autoLoadMedia = prefs[Keys.AUTO_LOAD_MEDIA] ?: true,
            fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f,
            backgroundSyncEnabled = prefs[Keys.BACKGROUND_SYNC_ENABLED] ?: false,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 60,
            notifyFavoritesOnly = prefs[Keys.NOTIFY_FAVORITES_ONLY] ?: true,
            lastBackgroundSyncAt = prefs[Keys.LAST_BACKGROUND_SYNC_AT] ?: 0L,
            offlinePrefetchPolicy = prefs[Keys.OFFLINE_PREFETCH_POLICY]?.let { name ->
                OfflinePrefetchPolicy.entries.firstOrNull { it.name == name }
            } ?: OfflinePrefetchPolicy.WIFI_ONLY,
            offlineMaxCacheMb = prefs[Keys.OFFLINE_MAX_CACHE_MB] ?: 150,
            offlineCacheFullRssArticles = prefs[Keys.OFFLINE_CACHE_RSS_ARTICLES] ?: true
        )
    }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

    suspend fun setTransportMode(mode: TransportMode) {
        context.dataStore.edit { it[Keys.TRANSPORT_MODE] = mode.name }
    }

    suspend fun setBridgeLangProfile(profile: BridgeLangProfile) {
        context.dataStore.edit { it[Keys.BRIDGE_LANG_PROFILE] = profile.name }
    }

    suspend fun setUseDoH(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DOH] = enabled }
    }

    suspend fun setDohProvider(provider: DohProvider) {
        context.dataStore.edit { it[Keys.DOH_PROVIDER] = provider.name }
    }

    suspend fun setCustomMirrorUrl(url: String) {
        context.dataStore.edit { it[Keys.CUSTOM_MIRROR_URL] = url.trim() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAutoLoadMedia(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_LOAD_MEDIA] = enabled }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.35f) }
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes.coerceAtLeast(15) }
    }

    suspend fun setNotifyFavoritesOnly(favoritesOnly: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_FAVORITES_ONLY] = favoritesOnly }
    }

    suspend fun setLastBackgroundSyncAt(timestampMs: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKGROUND_SYNC_AT] = timestampMs }
    }

    suspend fun setOfflinePrefetchPolicy(policy: OfflinePrefetchPolicy) {
        context.dataStore.edit { it[Keys.OFFLINE_PREFETCH_POLICY] = policy.name }
    }

    suspend fun setOfflineMaxCacheMb(maxMb: Int) {
        context.dataStore.edit { it[Keys.OFFLINE_MAX_CACHE_MB] = maxMb.coerceIn(50, 500) }
    }

    suspend fun setOfflineCacheFullRssArticles(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_CACHE_RSS_ARTICLES] = enabled }
    }
}
