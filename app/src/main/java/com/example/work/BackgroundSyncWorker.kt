package com.example.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.OpenFeedApplication
import com.example.R
import com.example.data.network.DeepLinkParser

class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OpenFeedApplication ?: return Result.failure()
        val container = app.container
        val repository = container.repository
        val prefs = container.preferencesRepository
        val settings = prefs.getCurrentSettings()

        val channels = repository.getChannelsSync()
        if (channels.isEmpty()) {
            prefs.setLastBackgroundSyncAt(System.currentTimeMillis())
            return Result.success()
        }

        val targetChannels = if (settings.notifyFavoritesOnly) {
            channels.filter { it.isFavorite }.ifEmpty { channels }
        } else {
            channels
        }

        var anySucceeded = false
        val newUpdates = mutableListOf<Triple<String, String, Int>>() // username, title, newCount

        for (channel in targetChannels) {
            val prevMaxId = repository.getMaxPostIdForChannel(channel.username) ?: 0L
            runCatching {
                val updated = repository.refreshChannelByKey(channel.username, autoPrefetch = false)
                anySucceeded = true
                if (prevMaxId > 0L) {
                    val newMaxId = repository.getMaxPostIdForChannel(channel.username) ?: prevMaxId
                    if (newMaxId != prevMaxId) {
                        val delta = if (updated.sourceType == com.example.data.model.FeedSourceType.TELEGRAM && newMaxId > prevMaxId) {
                            (newMaxId - prevMaxId).toInt().coerceAtLeast(1)
                        } else {
                            1
                        }
                        newUpdates.add(Triple(channel.username, updated.title, delta))
                    }
                }
            }
        }

        if (anySucceeded) {
            runCatching {
                repository.autoPrefetchLatestIfAllowed()
            }
        }

        prefs.setLastBackgroundSyncAt(System.currentTimeMillis())

        if (newUpdates.isNotEmpty()) {
            postSummaryNotification(newUpdates)
        }

        return if (anySucceeded || targetChannels.isEmpty()) Result.success() else Result.retry()
    }

    private fun postSummaryNotification(updates: List<Triple<String, String, Int>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel()

        val firstChannel = updates.first()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DeepLinkParser.EXTRA_CHANNEL_USERNAME, firstChannel.first)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (updates.size == 1) {
            "${firstChannel.second} • New posts"
        } else {
            "${updates.size} feeds updated on OpenFeed"
        }

        val body = updates.joinToString(" • ") { "${it.second} (+${it.third})" }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Channel Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when subscribed Telegram channels have new posts"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "openfeed_new_posts"
        const val NOTIFICATION_ID = 4102
    }
}
