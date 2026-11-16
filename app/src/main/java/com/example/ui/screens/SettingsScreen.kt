package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.BridgeLangProfile
import com.example.data.model.DohProvider
import com.example.data.model.OfflinePrefetchPolicy
import com.example.data.model.ThemeMode
import com.example.data.model.TransportMode
import com.example.ui.FeedUiState
import com.example.ui.theme.resolveIsDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: FeedUiState,
    onSetTransportMode: (TransportMode) -> Unit,
    onSetBridgeLangProfile: (BridgeLangProfile) -> Unit,
    onSetUseDoH: (Boolean) -> Unit,
    onSetDohProvider: (DohProvider) -> Unit,
    onSaveCustomMirror: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAutoLoadMedia: (Boolean) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onRunDiagnostics: () -> Unit,
    onClearCache: () -> Unit,
    onExportJsonBackup: (android.net.Uri) -> Unit,
    onExportOpmlBackup: (android.net.Uri) -> Unit,
    onImportBackup: (android.net.Uri) -> Unit,
    onSetBackgroundSyncEnabled: (Boolean) -> Unit,
    onSetSyncIntervalMinutes: (Int) -> Unit,
    onSetNotifyFavoritesOnly: (Boolean) -> Unit,
    onTriggerBackgroundSyncNow: () -> Unit,
    onSetOfflinePrefetchPolicy: (OfflinePrefetchPolicy) -> Unit = {},
    onSetOfflineMaxCacheMb: (Int) -> Unit = {},
    onSetOfflineCacheFullRssArticles: (Boolean) -> Unit = {},
    onPrepareOfflineNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.settings
    var mirrorDraft by remember(settings.customMirrorUrl) {
        mutableStateOf(settings.customMirrorUrl)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onSetBackgroundSyncEnabled(true)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportJsonBackup(uri)
    }

    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) onExportOpmlBackup(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackup(uri)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Standalone OpenFeed Engine • No Termux Required",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Network & Bypass Engine
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.Public,
                    title = stringResource(R.string.settings_transport_section)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Transport Route",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TransportMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.transportMode == mode,
                                    onClick = { onSetTransportMode(mode) },
                                    modifier = Modifier.testTag("transport_radio_${mode.name}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (settings.transportMode == TransportMode.CUSTOM_MIRROR) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = mirrorDraft,
                                    onValueChange = { mirrorDraft = it },
                                    label = { Text("Mirror Base URL") },
                                    placeholder = { Text("https://my-relay.workers.dev") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onSaveCustomMirror(mirrorDraft) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Save")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Google Translate Bridge Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BridgeLangProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = settings.bridgeLangProfile == profile,
                                    onClick = { onSetBridgeLangProfile(profile) },
                                    label = { Text(profile.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DNS-over-HTTPS (DoH)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Bypass ISP DNS poisoning with encrypted lookups",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.useDoH,
                                onCheckedChange = onSetUseDoH,
                                modifier = Modifier.testTag("switch_doh")
                            )
                        }

                        if (settings.useDoH) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DohProvider.entries.forEach { provider ->
                                    FilterChip(
                                        selected = settings.dohProvider == provider,
                                        onClick = { onSetDohProvider(provider) },
                                        label = { Text(provider.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Background Sync (WorkManager)
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.settings_sync_section)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Periodic Background Sync",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Check channels in background & notify on new posts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.backgroundSyncEnabled,
                                onCheckedChange = { wantEnable ->
                                    if (!wantEnable) {
                                        onSetBackgroundSyncEnabled(false)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onSetBackgroundSyncEnabled(true)
                                    }
                                },
                                modifier = Modifier.testTag("switch_background_sync")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sync Interval",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val intervals = listOf(15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h", 360 to "6h")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            intervals.forEach { (minutes, label) ->
                                FilterChip(
                                    selected = settings.syncIntervalMinutes == minutes,
                                    onClick = { onSetSyncIntervalMinutes(minutes) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("sync_interval_$minutes")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Favorites Only", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Only sync & notify for starred channels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.notifyFavoritesOnly,
                                onCheckedChange = onSetNotifyFavoritesOnly,
                                modifier = Modifier.testTag("switch_notify_favorites")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val lastSyncLabel = remember(settings.lastBackgroundSyncAt) {
                                if (settings.lastBackgroundSyncAt <= 0L) {
                                    "Never run yet"
                                } else {
                                    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                    "Last sync: ${fmt.format(Date(settings.lastBackgroundSyncAt))}"
                                }
                            }
                            Text(
                                text = lastSyncLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = onTriggerBackgroundSyncNow,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("sync_now_worker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Now")
                            }
                        }
                    }
                }
            }

            // Section 3: Backup & Restore (Export / Import)
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.Backup,
                    title = stringResource(R.string.settings_backup_section)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Export subscribed channels & bookmarked posts, or restore from JSON, OPML, or plain channel lists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    exportJsonLauncher.launch("openfeed-backup-$stamp.json")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("export_json_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.export_backup))
                            }
                            OutlinedButton(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                    exportOpmlLauncher.launch("openfeed-channels-$stamp.opml")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("export_opml_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export OPML")
                            }
                            OutlinedButton(
                                onClick = {
                                    importBackupLauncher.launch(
                                        arrayOf("application/json", "text/xml", "application/xml", "text/plain", "*/*")
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("import_backup_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.import_backup))
                            }
                        }
                    }
                }
            }

            // Section 4: Diagnostics
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.NetworkCheck,
                    title = "Connection Diagnostics"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val diag = uiState.diagnostics
                        Button(
                            onClick = onRunDiagnostics,
                            enabled = !diag.isRunning,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_diagnostics_button")
                        ) {
                            if (diag.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing routes…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.test_connection))
                            }
                        }

                        if (diag.dohReachable != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            DiagnosticRow(
                                label = "DoH (${settings.dohProvider.bootstrapIp})",
                                ok = diag.dohReachable == true,
                                detail = buildString {
                                    diag.dohLatencyMs?.let { append("${it}ms") }
                                    diag.dohResolvedIp?.let { append(" • IP $it") }
                                }
                            )
                            DiagnosticRow(
                                label = "Translate Bridge (*.translate.goog)",
                                ok = diag.bridgeReachable == true,
                                detail = diag.bridgeLatencyMs?.let { "${it}ms" } ?: "Unreachable"
                            )
                            DiagnosticRow(
                                label = "Direct (t.me/s)",
                                ok = diag.directReachable == true,
                                detail = diag.directLatencyMs?.let { "${it}ms" } ?: "Blocked / Timeout"
                            )
                            if (diag.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = diag.summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Reading & Appearance
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_appearance_section)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val isDark = resolveIsDarkTheme(settings.themeMode)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.dark_mode_title),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.dark_mode_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isDark,
                                onCheckedChange = { enableDark ->
                                    onSetThemeMode(if (enableDark) ThemeMode.DARK else ThemeMode.LIGHT)
                                },
                                modifier = Modifier.testTag("switch_dark_mode")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You Dynamic Color", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Match Android 12+ wallpaper palette",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = onSetDynamicColor,
                                modifier = Modifier.testTag("switch_dynamic_color")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-load Photos & Previews", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Disable for ultra-low bandwidth text mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoLoadMedia,
                                onCheckedChange = onSetAutoLoadMedia
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Post Text Scale (${(settings.fontScale * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = settings.fontScale,
                            onValueChange = onSetFontScale,
                            valueRange = 0.85f..1.35f,
                            steps = 4
                        )
                    }
                }
            }

            // Section 6: Offline Cache & Photo Vault
            item {
                SettingsSectionHeader(
                    icon = Icons.Rounded.OfflinePin,
                    title = stringResource(R.string.settings_storage_section)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.offline_prefetch_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.offline_prefetch_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OfflinePrefetchPolicy.entries.forEach { policy ->
                                FilterChip(
                                    selected = settings.offlinePrefetchPolicy == policy,
                                    onClick = { onSetOfflinePrefetchPolicy(policy) },
                                    label = { Text(policy.label) },
                                    modifier = Modifier.testTag("offline_policy_${policy.name}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.offline_rss_articles_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.offline_rss_articles_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.offlineCacheFullRssArticles,
                                onCheckedChange = onSetOfflineCacheFullRssArticles,
                                modifier = Modifier.testTag("switch_offline_rss_articles")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.offline_vault_limit_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(50, 150, 300).forEach { limitMb ->
                                FilterChip(
                                    selected = settings.offlineMaxCacheMb == limitMb,
                                    onClick = { onSetOfflineMaxCacheMb(limitMb) },
                                    label = { Text("$limitMb MB") },
                                    modifier = Modifier.testTag("offline_limit_$limitMb")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        val stats = uiState.offlineVaultStats
                        val mbFormatted = remember(stats.totalBytes) {
                            val mb = stats.totalBytes / (1024.0 * 1024.0)
                            String.format(Locale.US, "%.1f MB", mb)
                        }
                        Text(
                            text = stringResource(
                                R.string.offline_stats_summary,
                                uiState.cachedPostCount,
                                stats.cachedArticleCount,
                                stats.photoCount,
                                mbFormatted
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.offline_bookmarked_safe),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val prep = uiState.offlinePrepProgress
                        if (prep != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val fraction = if (prep.total > 0) {
                                (prep.completed.toFloat() / prep.total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPrepareOfflineNow,
                                enabled = prep == null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("prepare_offline_button")
                            ) {
                                if (prep != null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.offline_preparing, prep.completed, prep.total))
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.OfflinePin,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.offline_prepare_now))
                                }
                            }
                            OutlinedButton(
                                onClick = onClearCache,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("clear_cache_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.clear_cache))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    ok: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
