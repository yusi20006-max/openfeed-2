package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.OfflineMediaVault
import com.example.data.local.OfflineVaultCoilInterceptor
import com.example.data.local.OpenFeedDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.UserSettings
import com.example.data.network.OpenFeedNetworkClient
import com.example.data.repository.OpenFeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferencesRepository = UserPreferencesRepository(application)

    val latestSettings: StateFlow<UserSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = UserSettings()
        )

    val networkClient = OpenFeedNetworkClient(
        settingsProvider = { latestSettings.value }
    )

    val offlineMediaVault = OfflineMediaVault(application)

    private val database = OpenFeedDatabase.getInstance(application)

    val repository = OpenFeedRepository(
        dao = database.openFeedDao(),
        networkClient = networkClient,
        offlineMediaVault = offlineMediaVault,
        settingsProvider = { latestSettings.value }
    )
}

class OpenFeedApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = container.preferencesRepository.settingsFlow.first()
            if (settings.backgroundSyncEnabled) {
                com.example.work.BackgroundSyncScheduler.applySchedule(
                    this@OpenFeedApplication,
                    enabled = true,
                    intervalMinutes = settings.syncIntervalMinutes
                )
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(container.networkClient.httpClient)
            .components {
                add(OfflineVaultCoilInterceptor(container.offlineMediaVault))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("openfeed_image_cache"))
                    .maxSizeBytes(96L * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
