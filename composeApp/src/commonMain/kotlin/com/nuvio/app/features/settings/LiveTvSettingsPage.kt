package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.livetv.LiveTvPlaylist
import com.nuvio.app.features.livetv.LiveTvPlaylistType
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.LiveTvStalkerSettings
import com.nuvio.app.features.livetv.LiveTvUiState
import com.nuvio.app.features.livetv.LiveTvXtreamSettings
import com.nuvio.app.features.livetv.STALKER_PLAYLIST_ID
import com.nuvio.app.features.livetv.XTREAM_PLAYLIST_ID
import com.nuvio.app.features.livetv.rememberLiveTvPlaylistFilePicker
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_remove
import nuvio.composeapp.generated.resources.live_tv_connect
import nuvio.composeapp.generated.resources.live_tv_disconnect
import nuvio.composeapp.generated.resources.live_tv_password
import nuvio.composeapp.generated.resources.live_tv_settings_add_local_playlist
import nuvio.composeapp.generated.resources.live_tv_settings_add_url_playlist
import nuvio.composeapp.generated.resources.live_tv_settings_cancel_edit
import nuvio.composeapp.generated.resources.live_tv_settings_configured_playlists
import nuvio.composeapp.generated.resources.live_tv_settings_description
import nuvio.composeapp.generated.resources.live_tv_settings_edit_playlist
import nuvio.composeapp.generated.resources.live_tv_settings_navigation_description
import nuvio.composeapp.generated.resources.live_tv_settings_navigation_title
import nuvio.composeapp.generated.resources.live_tv_settings_no_playlists
import nuvio.composeapp.generated.resources.live_tv_settings_pick_file_failed
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_disabled
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_enabled
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_label
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_name_label
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_name_placeholder
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_placeholder
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_source_local
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_type_local
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_type_url
import nuvio.composeapp.generated.resources.live_tv_settings_playlist_url_label
import nuvio.composeapp.generated.resources.live_tv_settings_save_playlist
import nuvio.composeapp.generated.resources.live_tv_settings_section_navigation
import nuvio.composeapp.generated.resources.live_tv_settings_section_playlist
import nuvio.composeapp.generated.resources.live_tv_settings_section_providers
import nuvio.composeapp.generated.resources.live_tv_stalker_description
import nuvio.composeapp.generated.resources.live_tv_stalker_mac
import nuvio.composeapp.generated.resources.live_tv_stalker_portal
import nuvio.composeapp.generated.resources.live_tv_stalker_title
import nuvio.composeapp.generated.resources.live_tv_username
import nuvio.composeapp.generated.resources.live_tv_xtream_description
import nuvio.composeapp.generated.resources.live_tv_xtream_server
import nuvio.composeapp.generated.resources.live_tv_xtream_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.liveTvSettingsContent(
    isTablet: Boolean,
    uiState: LiveTvUiState,
) {
    if (uiState.hasPlaylist) {
        item {
            SettingsSection(
                title = stringResource(Res.string.live_tv_settings_section_navigation),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    LiveTvNavigationVisibilityRow(
                        isTablet = isTablet,
                        enabled = uiState.isNavigationEnabled,
                        onEnabledChanged = LiveTvRepository::setNavigationEnabled,
                    )
                }
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.live_tv_settings_section_providers),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                LiveTvProviderSettingsRow(isTablet, uiState)
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.live_tv_settings_section_playlist),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                LiveTvPlaylistSourcesRow(
                    isTablet = isTablet,
                    uiState = uiState,
                    onUrlAdded = LiveTvRepository::addPlaylistUrl,
                    onLocalPlaylistAdded = LiveTvRepository::addLocalPlaylist,
                    onPlaylistUpdated = LiveTvRepository::updatePlaylist,
                    onPlaylistEnabledChanged = LiveTvRepository::setPlaylistEnabled,
                    onPlaylistRemoved = LiveTvRepository::removePlaylist,
                )
            }
        }
    }
}

@Composable
private fun LiveTvProviderSettingsRow(isTablet: Boolean, uiState: LiveTvUiState) {
    val padding = if (isTablet) 20.dp else 16.dp
    val scope = rememberCoroutineScope()

    var xtreamServer by rememberSaveable(uiState.xtreamSettings.serverUrl) { mutableStateOf(uiState.xtreamSettings.serverUrl) }
    var xtreamUser by rememberSaveable(uiState.xtreamSettings.username) { mutableStateOf(uiState.xtreamSettings.username) }
    var xtreamPassword by rememberSaveable(uiState.xtreamSettings.password) { mutableStateOf(uiState.xtreamSettings.password) }
    var isConnectingXtream by rememberSaveable { mutableStateOf(false) }
    var xtreamFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var xtreamFeedbackIsError by rememberSaveable { mutableStateOf(false) }

    var stalkerPortal by rememberSaveable(uiState.stalkerSettings.portalUrl) { mutableStateOf(uiState.stalkerSettings.portalUrl) }
    var stalkerMac by rememberSaveable(uiState.stalkerSettings.macAddress) { mutableStateOf(uiState.stalkerSettings.macAddress) }
    var stalkerUser by rememberSaveable(uiState.stalkerSettings.username) { mutableStateOf(uiState.stalkerSettings.username) }
    var stalkerPassword by rememberSaveable(uiState.stalkerSettings.password) { mutableStateOf(uiState.stalkerSettings.password) }
    var isConnectingStalker by rememberSaveable { mutableStateOf(false) }
    var stalkerFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var stalkerFeedbackIsError by rememberSaveable { mutableStateOf(false) }

    val xtreamChannelCount = uiState.channels.count { it.playlistId == XTREAM_PLAYLIST_ID }
    val stalkerChannelCount = uiState.channels.count { it.playlistId == STALKER_PLAYLIST_ID }

    Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(Res.string.live_tv_xtream_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.xtreamSettings.isConfigured) {
                Text(
                    text = if (xtreamChannelCount > 0) "✅ Đã kết nối ($xtreamChannelCount kênh)" else "✅ Đã kết nối",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiveTvPlaylistEnabledColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(stringResource(Res.string.live_tv_xtream_description), color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (xtreamFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (xtreamFeedbackIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else LiveTvPlaylistEnabledColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = xtreamFeedback ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (xtreamFeedbackIsError) MaterialTheme.colorScheme.error else LiveTvPlaylistEnabledColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedTextField(
            xtreamServer,
            { xtreamServer = it; xtreamFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingXtream,
            label = { Text(stringResource(Res.string.live_tv_xtream_server)) }
        )
        OutlinedTextField(
            xtreamUser,
            { xtreamUser = it; xtreamFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingXtream,
            label = { Text(stringResource(Res.string.live_tv_username)) }
        )
        OutlinedTextField(
            xtreamPassword,
            { xtreamPassword = it; xtreamFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingXtream,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(Res.string.live_tv_password)) }
        )

        if (isConnectingXtream) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Đang kết nối tới máy chủ Xtream...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        isConnectingXtream = true
                        xtreamFeedback = null
                        val result = LiveTvRepository.testAndSaveXtreamSettings(
                            LiveTvXtreamSettings(xtreamServer.trim(), xtreamUser.trim(), xtreamPassword.trim())
                        )
                        isConnectingXtream = false
                        result.onSuccess { count ->
                            xtreamFeedbackIsError = false
                            xtreamFeedback = "Kết nối Xtream thành công! Đã tải $count kênh."
                        }.onFailure { err ->
                            xtreamFeedbackIsError = true
                            xtreamFeedback = err.message ?: "Kết nối Xtream thất bại."
                        }
                    }
                },
                enabled = xtreamServer.isNotBlank() && xtreamUser.isNotBlank() && xtreamPassword.isNotBlank() && !isConnectingXtream,
            ) {
                Text(if (isConnectingXtream) "Đang kết nối..." else stringResource(Res.string.live_tv_connect))
            }
            if (uiState.xtreamSettings.isConfigured && !isConnectingXtream) {
                OutlinedButton(onClick = {
                    LiveTvRepository.removeXtream()
                    xtreamFeedback = null
                }) {
                    Text(stringResource(Res.string.live_tv_disconnect))
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(Res.string.live_tv_stalker_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.stalkerSettings.isConfigured) {
                Text(
                    text = if (stalkerChannelCount > 0) "✅ Đã kết nối ($stalkerChannelCount kênh)" else "✅ Đã kết nối",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiveTvPlaylistEnabledColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(stringResource(Res.string.live_tv_stalker_description), color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (stalkerFeedback != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (stalkerFeedbackIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else LiveTvPlaylistEnabledColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = stalkerFeedback ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stalkerFeedbackIsError) MaterialTheme.colorScheme.error else LiveTvPlaylistEnabledColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedTextField(
            stalkerPortal,
            { stalkerPortal = it; stalkerFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingStalker,
            label = { Text(stringResource(Res.string.live_tv_stalker_portal)) }
        )
        OutlinedTextField(
            stalkerMac,
            { stalkerMac = it.uppercase(); stalkerFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingStalker,
            label = { Text(stringResource(Res.string.live_tv_stalker_mac)) }
        )
        OutlinedTextField(
            stalkerUser,
            { stalkerUser = it; stalkerFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingStalker,
            label = { Text(stringResource(Res.string.live_tv_username)) }
        )
        OutlinedTextField(
            stalkerPassword,
            { stalkerPassword = it; stalkerFeedback = null },
            Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnectingStalker,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text(stringResource(Res.string.live_tv_password)) }
        )

        if (isConnectingStalker) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "Đang kết nối tới Stalker Portal...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        isConnectingStalker = true
                        stalkerFeedback = null
                        val result = LiveTvRepository.testAndSaveStalkerSettings(
                            LiveTvStalkerSettings(stalkerPortal.trim(), stalkerMac.trim(), stalkerUser.trim(), stalkerPassword.trim())
                        )
                        isConnectingStalker = false
                        result.onSuccess { count ->
                            stalkerFeedbackIsError = false
                            stalkerFeedback = "Kết nối Stalker Portal thành công! Đã tải $count kênh."
                        }.onFailure { err ->
                            stalkerFeedbackIsError = true
                            stalkerFeedback = err.message ?: "Kết nối Stalker Portal thất bại."
                        }
                    }
                },
                enabled = stalkerPortal.isNotBlank() && stalkerMac.isNotBlank() && !isConnectingStalker,
            ) {
                Text(if (isConnectingStalker) "Đang kết nối..." else stringResource(Res.string.live_tv_connect))
            }
            if (uiState.stalkerSettings.isConfigured && !isConnectingStalker) {
                OutlinedButton(onClick = {
                    LiveTvRepository.removeStalker()
                    stalkerFeedback = null
                }) {
                    Text(stringResource(Res.string.live_tv_disconnect))
                }
            }
        }
    }
}

@Composable
private fun LiveTvNavigationVisibilityRow(
    isTablet: Boolean,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.live_tv_settings_navigation_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.live_tv_settings_navigation_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
    }
}

@Composable
private fun LiveTvPlaylistSourcesRow(
    isTablet: Boolean,
    uiState: LiveTvUiState,
    onUrlAdded: (String?, String) -> Unit,
    onLocalPlaylistAdded: (String?, String?, String) -> Unit,
    onPlaylistUpdated: (playlistId: String, name: String, source: String) -> Unit,
    onPlaylistEnabledChanged: (playlistId: String, isEnabled: Boolean) -> Unit,
    onPlaylistRemoved: (String) -> Unit,
) {
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftUrl by rememberSaveable { mutableStateOf("") }
    var filePickerError by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedName = draftName.trim()
    val normalizedUrl = draftUrl.trim()
    val fallbackPickFileFailed = stringResource(Res.string.live_tv_settings_pick_file_failed)
    val filePicker = rememberLiveTvPlaylistFilePicker(
        onPlaylistLoaded = { fileName, content ->
            filePickerError = null
            onLocalPlaylistAdded(normalizedName.takeIf(String::isNotBlank), fileName, content)
            draftName = ""
        },
        onError = { message ->
            filePickerError = message.ifBlank { fallbackPickFileFailed }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.live_tv_settings_playlist_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.live_tv_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(Res.string.live_tv_settings_playlist_name_placeholder)) },
            colors = liveTvOutlinedTextFieldColors(),
        )

        OutlinedTextField(
            value = draftUrl,
            onValueChange = { draftUrl = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(Res.string.live_tv_settings_playlist_placeholder)) },
            colors = liveTvOutlinedTextFieldColors(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    onUrlAdded(normalizedName.takeIf(String::isNotBlank), normalizedUrl)
                    draftName = ""
                    draftUrl = ""
                },
                enabled = normalizedUrl.isNotBlank(),
            ) {
                Text(stringResource(Res.string.live_tv_settings_add_url_playlist))
            }
            OutlinedButton(
                onClick = filePicker::launch,
                enabled = filePicker.canPickFiles,
            ) {
                Text(stringResource(Res.string.live_tv_settings_add_local_playlist))
            }
        }

        filePickerError?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text(
            text = stringResource(Res.string.live_tv_settings_configured_playlists),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )

        if (uiState.playlists.isEmpty()) {
            Text(
                text = stringResource(Res.string.live_tv_settings_no_playlists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.playlists.forEach { playlist ->
                    LiveTvPlaylistRow(
                        playlist = playlist,
                        onUpdate = { name, source -> onPlaylistUpdated(playlist.id, name, source) },
                        onEnabledChanged = { isEnabled -> onPlaylistEnabledChanged(playlist.id, isEnabled) },
                        onRemove = { onPlaylistRemoved(playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvPlaylistRow(
    playlist: LiveTvPlaylist,
    onUpdate: (name: String, source: String) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var isEditing by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var draftName by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var draftSource by rememberSaveable(playlist.id) { mutableStateOf(playlist.source) }
    val sourceLabel = when (playlist.type) {
        LiveTvPlaylistType.Url -> playlist.source
        LiveTvPlaylistType.LocalFile -> stringResource(Res.string.live_tv_settings_playlist_source_local)
    }
    val enabledLabel = stringResource(
        if (playlist.isEnabled) {
            Res.string.live_tv_settings_playlist_enabled
        } else {
            Res.string.live_tv_settings_playlist_disabled
        },
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isEditing) {
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(Res.string.live_tv_settings_playlist_name_label)) },
                colors = liveTvOutlinedTextFieldColors(),
            )
            if (playlist.type == LiveTvPlaylistType.Url) {
                OutlinedTextField(
                    value = draftSource,
                    onValueChange = { draftSource = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.live_tv_settings_playlist_url_label)) },
                    colors = liveTvOutlinedTextFieldColors(),
                )
            } else {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onUpdate(draftName, draftSource)
                        isEditing = false
                    },
                    enabled = draftName.trim().isNotBlank() && draftSource.trim().isNotBlank(),
                ) {
                    Text(stringResource(Res.string.live_tv_settings_save_playlist))
                }
                OutlinedButton(
                    onClick = {
                        draftName = playlist.name
                        draftSource = playlist.source
                        isEditing = false
                    },
                ) {
                    Text(stringResource(Res.string.live_tv_settings_cancel_edit))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = playlist.name,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "($enabledLabel)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (playlist.isEnabled) {
                                    LiveTvPlaylistEnabledColor
                                } else {
                                    LiveTvPlaylistDisabledColor
                                },
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = playlist.isEnabled,
                        onCheckedChange = onEnabledChanged,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { isEditing = true }) {
                        Text(stringResource(Res.string.live_tv_settings_edit_playlist))
                    }
                    TextButton(onClick = onRemove) {
                        Text(stringResource(Res.string.action_remove))
                    }
                }
            }
        }
    }
}

private val LiveTvPlaylistEnabledColor = Color(0xFF2E7D32)
private val LiveTvPlaylistDisabledColor = Color(0xFFC62828)

@Composable
private fun liveTvOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)
