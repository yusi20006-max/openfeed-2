package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BridgeLangProfile
import com.example.data.model.DohProvider
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
            fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f
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
}
