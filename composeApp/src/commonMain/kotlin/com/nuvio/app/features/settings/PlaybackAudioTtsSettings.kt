package com.nuvio.app.features.settings

import com.nuvio.app.features.player.tts.filterSystemVoicesByLanguage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.tts.InstalledPiperModel
import com.nuvio.app.features.player.tts.PiperModelManager
import com.nuvio.app.features.player.tts.PiperOnnxEngine
import com.nuvio.app.features.player.tts.SubtitleTtsCoordinator
import com.nuvio.app.features.player.tts.SubtitleTtsEngineType
import com.nuvio.app.features.player.tts.SubtitleTtsSettings
import com.nuvio.app.features.player.tts.SubtitleTtsTimingCalculator
import com.nuvio.app.features.player.tts.SystemTtsEngine
import com.nuvio.app.features.player.tts.SystemVoiceInfo
import com.nuvio.app.isIos
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_done
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.random.Random

private const val PERCENT_SCALE = 100f
private const val PITCH_MIN_PERCENT = 60
private const val PITCH_MAX_PERCENT = 150
private const val VOLUME_MIN_PERCENT = 0
private const val VOLUME_MAX_PERCENT = 100
private const val SPEED_MIN_PERCENT = 80
private val SPEED_MAX_PERCENT =
    (SubtitleTtsTimingCalculator.MAX_BASE_SPEED * PERCENT_SCALE).roundToInt()
private const val PERCENT_STEP = 5
private const val DECIMAL_BASE = 10
private val MAX_AUTO_SPEED_MIN_PERCENT =
    (SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED * PERCENT_SCALE).roundToInt()
private val MAX_AUTO_SPEED_MAX_PERCENT =
    (SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED * PERCENT_SCALE).roundToInt()
private val VOICE_DIALOG_MAX_HEIGHT = 420.dp

/**
 * Subtitle TTS settings section rendered under Playback -> Audio.
 */
@Composable
internal fun PlaybackAudioTtsSettings(
    isTablet: Boolean,
) {
    LaunchedEffect(Unit) {
        SubtitleTtsCoordinator.refreshInstalledPiperModels()
    }

    val tokens = MaterialTheme.nuvio
    val ttsSettings by SubtitleTtsCoordinator.settings.collectAsStateWithLifecycle()
    val coordinatorStatusMessage by SubtitleTtsCoordinator.statusMessage.collectAsStateWithLifecycle()
    val availableEngines by SystemTtsEngine.availableEngines.collectAsStateWithLifecycle()
    val availableVoices by SystemTtsEngine.availableVoices.collectAsStateWithLifecycle()
    val downloadProgress by PiperModelManager.downloadProgress.collectAsStateWithLifecycle()
    val isEspeakDownloading by PiperModelManager.isEspeakDownloading.collectAsStateWithLifecycle()
    val espeakProgress by PiperModelManager.espeakDownloadProgress.collectAsStateWithLifecycle()

    val installedModels = remember(ttsSettings.installedPiperModels, downloadProgress) {
        val diskPresetModels = PiperModelManager.presets.mapNotNull { preset ->
            PiperModelManager.getInstalledModel(preset.id)
        }
        (ttsSettings.installedPiperModels + diskPresetModels).distinctBy { it.id }
    }
    val effectiveActiveModel = remember(installedModels, ttsSettings.activePiperModelId) {
        installedModels.firstOrNull { it.id == ttsSettings.activePiperModelId }
            ?: installedModels.firstOrNull()
    }

    // Keep saved installedPiperModels and activePiperModelId aligned with disk state.
    LaunchedEffect(installedModels, ttsSettings.activePiperModelId) {
        val missingFromSettings = installedModels.filter { candidate ->
            ttsSettings.installedPiperModels.none { it.id == candidate.id }
        }
        val needsActive = installedModels.isNotEmpty() &&
            (ttsSettings.activePiperModelId == null || installedModels.none { it.id == ttsSettings.activePiperModelId })
        if (missingFromSettings.isNotEmpty() || needsActive) {
            SubtitleTtsCoordinator.refreshInstalledPiperModels()
        }
    }

    var ttsErrorMessage by remember { mutableStateOf<String?>(null) }
    val activeBannerError = ttsErrorMessage ?: coordinatorStatusMessage?.takeIf { installedModels.isNotEmpty() }
    var pendingDownloads by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showEngineTypeDialog by remember { mutableStateOf(false) }
    var showSystemEngineDialog by remember { mutableStateOf(false) }
    var showSystemVoiceDialog by remember { mutableStateOf(false) }
    var showAddCustomModelDialog by remember { mutableStateOf(false) }

    fun clearErrors() {
        ttsErrorMessage = null
        SubtitleTtsCoordinator.clearStatusMessage()
    }

    SettingsSection(
        title = "Thuyết minh phụ đề (TTS Offline)",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Bật thuyết minh phụ đề",
                description = "Tự động đọc phụ đề bằng giọng đọc offline không cần mạng",
                checked = ttsSettings.enabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    SubtitleTtsCoordinator.updateSettings { it.copy(enabled = enabled) }
                },
            )

            if (ttsSettings.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Bộ máy giọng đọc",
                    description = engineSummaryDescription(
                        engineType = ttsSettings.engineType,
                        effectiveActiveModel = effectiveActiveModel,
                        hasInstalledModels = installedModels.isNotEmpty(),
                    ),
                    isTablet = isTablet,
                    onClick = { showEngineTypeDialog = true },
                )

                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Nghe thử giọng đọc",
                    description = "Phát câu: \"${SubtitleTtsCoordinator.SAMPLE_TEST_SENTENCE}\"",
                    isTablet = isTablet,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Nghe thử giọng đọc",
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(tokens.icons.lg),
                        )
                    },
                    onClick = {
                        clearErrors()
                        SubtitleTtsCoordinator.previewVoice(
                            overrideModel = if (ttsSettings.engineType == SubtitleTtsEngineType.PIPER_ONNX) {
                                effectiveActiveModel
                            } else {
                                null
                            },
                        )
                    },
                )

                if (ttsSettings.engineType == SubtitleTtsEngineType.SYSTEM) {
                    SystemTtsSettingsRows(
                        isTablet = isTablet,
                        ttsSettings = ttsSettings,
                        availableEngines = availableEngines.map { it.packageName to it.label },
                        availableVoices = availableVoices,
                        onOpenEngineDialog = { showSystemEngineDialog = true },
                        onOpenVoiceDialog = { showSystemVoiceDialog = true },
                    )
                } else {
                    PiperOnnxSettingsRows(
                        isTablet = isTablet,
                        installedModels = installedModels,
                        activeModelId = effectiveActiveModel?.id,
                        downloadProgress = downloadProgress,
                        pendingDownloads = pendingDownloads,
                        isEspeakDownloading = isEspeakDownloading,
                        espeakProgress = espeakProgress,
                        errorMessage = activeBannerError,
                        onDismissError = { clearErrors() },
                        onClearError = { clearErrors() },
                        onStartDownload = { id, name ->
                            pendingDownloads = pendingDownloads + (id to name)
                        },
                        onFinishDownload = { id ->
                            pendingDownloads = pendingDownloads - id
                        },
                        onError = { id, message ->
                            pendingDownloads = pendingDownloads - id
                            ttsErrorMessage = "Lỗi tải Piper: $message"
                        },
                        onOpenAddCustomDialog = { showAddCustomModelDialog = true },
                    )
                }

                GeneralTtsAudioRows(isTablet = isTablet, ttsSettings = ttsSettings)
            }
        }
    }

    if (showEngineTypeDialog) {
        SettingsChoiceDialog(
            title = "Chọn bộ máy giọng đọc",
            options = SubtitleTtsEngineType.entries,
            selected = ttsSettings.engineType,
            label = { type ->
                when (type) {
                    SubtitleTtsEngineType.SYSTEM -> "Google Speech / Hệ thống Android (Nhẹ, ổn định)"
                    SubtitleTtsEngineType.PIPER_ONNX -> "Piper AI (Mô hình ONNX Offline)"
                }
            },
            description = { type ->
                when (type) {
                    SubtitleTtsEngineType.SYSTEM -> "Sử dụng engine TTS có sẵn trên máy, cực nhẹ, chỉnh được cao độ pitch & giọng tiếng Việt."
                    SubtitleTtsEngineType.PIPER_ONNX -> "Mô hình VITS ONNX chất lượng cao tải từ HuggingFace, chạy offline 100% bằng sherpa-onnx."
                }
            },
            onSelect = { type ->
                clearErrors()
                SubtitleTtsCoordinator.updateSettings { current ->
                    val syncedModels = if (type == SubtitleTtsEngineType.PIPER_ONNX) {
                        val diskModels = PiperModelManager.presets.mapNotNull { PiperModelManager.getInstalledModel(it.id) }
                        (current.installedPiperModels + diskModels).distinctBy { it.id }
                    } else {
                        current.installedPiperModels
                    }
                    val nextActive = current.activePiperModelId
                        ?.takeIf { id -> syncedModels.any { it.id == id } }
                        ?: syncedModels.firstOrNull()?.id
                    current.copy(
                        engineType = type,
                        installedPiperModels = syncedModels,
                        activePiperModelId = nextActive,
                    )
                }
                showEngineTypeDialog = false
            },
            onDismiss = { showEngineTypeDialog = false },
        )
    }

    if (showSystemEngineDialog && availableEngines.isNotEmpty()) {
        val selectedEngine = availableEngines.firstOrNull { it.packageName == ttsSettings.systemEnginePackage }
            ?: availableEngines.first()
        SettingsChoiceDialog(
            title = "Chọn Engine TTS trên máy",
            options = availableEngines,
            selected = selectedEngine,
            label = { it.label },
            description = { it.packageName },
            onSelect = { engine ->
                SubtitleTtsCoordinator.updateSettings { it.copy(systemEnginePackage = engine.packageName) }
                showSystemEngineDialog = false
            },
            onDismiss = { showSystemEngineDialog = false },
        )
    }

    if (showSystemVoiceDialog && availableVoices.isNotEmpty()) {
        val filteredVoices = remember(availableVoices) {
            filterSystemVoicesByLanguage(availableVoices, null)
        }
        SystemVoiceSelectionDialog(
            voices = filteredVoices,
            selectedVoiceName = ttsSettings.systemVoiceName,
            onSelect = { voiceName ->
                SubtitleTtsCoordinator.updateSettings { it.copy(systemVoiceName = voiceName) }
                showSystemVoiceDialog = false
            },
            onPreview = { voiceName ->
                SubtitleTtsCoordinator.previewVoice(overrideVoiceName = voiceName)
            },
            onDismiss = { showSystemVoiceDialog = false },
        )
    }

    if (showAddCustomModelDialog) {
        AddCustomPiperModelDialog(
            onDownload = { name, onnxUrl, jsonUrl ->
                clearErrors()
                val customId = "custom_${Random.nextLong(1_000_000_000L, 9_999_999_999L)}"
                pendingDownloads = pendingDownloads + (customId to name)
                PiperModelManager.downloadCustomModel(
                    id = customId,
                    name = name,
                    onnxUrl = onnxUrl,
                    jsonUrl = jsonUrl,
                    onComplete = { installedModel ->
                        pendingDownloads = pendingDownloads - customId
                        selectInstalledPiperModel(installedModel)
                    },
                    onError = { error ->
                        pendingDownloads = pendingDownloads - customId
                        ttsErrorMessage = "Lỗi tải Piper: $error"
                    },
                )
            },
            onDismiss = { showAddCustomModelDialog = false },
        )
    }
}

private fun engineSummaryDescription(
    engineType: SubtitleTtsEngineType,
    effectiveActiveModel: InstalledPiperModel?,
    hasInstalledModels: Boolean,
): String = when (engineType) {
    SubtitleTtsEngineType.SYSTEM -> "Google Speech / Hệ thống Android (Nhẹ, ổn định)"
    SubtitleTtsEngineType.PIPER_ONNX -> when {
        !PiperOnnxEngine.isSupported -> "Piper AI (ONNX) — Chỉ hỗ trợ Android (tạm dùng giọng hệ thống)"
        effectiveActiveModel != null -> "Piper AI (ONNX) — ${effectiveActiveModel.name}"
        !hasInstalledModels -> "Piper AI (ONNX) — Chưa tải model, tạm dùng Google Speech"
        else -> "Piper AI (Mô hình ONNX Offline)"
    }
}

@Composable
private fun SystemTtsSettingsRows(
    isTablet: Boolean,
    ttsSettings: SubtitleTtsSettings,
    availableEngines: List<Pair<String, String>>,
    availableVoices: List<SystemVoiceInfo>,
    onOpenEngineDialog: () -> Unit,
    onOpenVoiceDialog: () -> Unit,
) {
    if (availableEngines.isNotEmpty()) {
        SettingsGroupDivider(isTablet = isTablet)
        val currentEngineLabel = availableEngines.firstOrNull {
            it.first == ttsSettings.systemEnginePackage
        }?.second ?: "Google Speech Services (Mặc định)"
        SettingsNavigationRow(
            title = "Bộ máy TTS hệ thống",
            description = currentEngineLabel,
            isTablet = isTablet,
            onClick = onOpenEngineDialog,
        )
    }

    SettingsGroupDivider(isTablet = isTablet)
    val currentVoiceLabel = availableVoices.firstOrNull {
        it.name == ttsSettings.systemVoiceName
    }?.displayName ?: if (availableVoices.isEmpty()) {
        "Đang tải danh sách giọng — chạm để thử lại"
    } else {
        "Giọng Tiếng Việt mặc định"
    }
    SettingsNavigationRow(
        title = "Giọng đọc hệ thống",
        description = currentVoiceLabel,
        isTablet = isTablet,
        onClick = {
            if (availableVoices.isEmpty()) {
                SystemTtsEngine.initialize(ttsSettings.systemEnginePackage)
            } else {
                onOpenVoiceDialog()
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSliderRow(
        title = "Cao độ giọng (Pitch)",
        value = (ttsSettings.systemPitch * PERCENT_SCALE).roundToInt(),
        valueText = { "$it%" },
        valueRange = PITCH_MIN_PERCENT..PITCH_MAX_PERCENT,
        step = PERCENT_STEP,
        isTablet = isTablet,
        onValueChange = { pitchPercent ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(systemPitch = pitchPercent / PERCENT_SCALE)
            }
        },
    )

    if (!isIos) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsNavigationRow(
            title = "Quản lý / Tải thêm giọng trên Android",
            description = "Mở cài đặt giọng đọc của máy để tải thêm gói dữ liệu giọng Google",
            isTablet = isTablet,
            onClick = { SystemTtsEngine.openSystemTtsSettings() },
        )
    }
}

@Composable
private fun PiperOnnxSettingsRows(
    isTablet: Boolean,
    installedModels: List<InstalledPiperModel>,
    activeModelId: String?,
    downloadProgress: Map<String, Float>,
    pendingDownloads: Map<String, String>,
    isEspeakDownloading: Boolean,
    espeakProgress: Float,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onClearError: () -> Unit,
    onStartDownload: (id: String, name: String) -> Unit,
    onFinishDownload: (id: String) -> Unit,
    onError: (id: String, message: String) -> Unit,
    onOpenAddCustomDialog: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val customModels = installedModels.filter { installed ->
        PiperModelManager.presets.none { it.id == installed.id }
    }
    val pendingCustomDownloads = pendingDownloads.filterKeys { pendingId ->
        PiperModelManager.presets.none { it.id == pendingId } &&
            customModels.none { it.id == pendingId }
    }

    SettingsGroupDivider(isTablet = isTablet)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Danh sách giọng Piper AI (Offline)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.textPrimary,
        )

        if (!PiperOnnxEngine.isSupported) {
            TtsStatusBanner(
                message = "Piper ONNX chỉ hỗ trợ trên Android. Ứng dụng sẽ dùng giọng đọc hệ thống.",
                isWarning = true,
            )
        } else if (installedModels.isEmpty()) {
            TtsStatusBanner(
                message = "Chưa có giọng Piper AI nào được tải. Hệ thống sẽ tạm dùng Google Speech / TTS mặc định cho đến khi bạn tải một giọng bên dưới.",
                isWarning = true,
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            TtsErrorBanner(
                message = errorMessage,
                onDismiss = onDismissError,
            )
        }

        if (isEspeakDownloading) {
            Text(
                text = "Đang tải dữ liệu ngữ âm espeak-ng: ${(espeakProgress * PERCENT_SCALE).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.accent,
            )
        }

        PiperModelManager.presets.forEach { preset ->
            val diskModel = installedModels.firstOrNull { it.id == preset.id }
                ?: PiperModelManager.getInstalledModel(preset.id)
            val progress = downloadProgress[preset.id]
            val isPending = preset.id in pendingDownloads
            PiperModelItemRow(
                name = preset.name,
                installedModel = diskModel,
                isSelected = diskModel != null && activeModelId == preset.id,
                downloadProgress = progress,
                isPendingDownload = isPending,
                downloadSupported = PiperOnnxEngine.isSupported,
                onSelect = { model ->
                    onClearError()
                    selectInstalledPiperModel(model)
                },
                onPreview = { model ->
                    onClearError()
                    SubtitleTtsCoordinator.previewVoice(overrideModel = model)
                },
                onDownload = {
                    if (!isPending && !downloadProgress.containsKey(preset.id)) {
                        onClearError()
                        onStartDownload(preset.id, preset.name)
                        PiperModelManager.downloadPreset(
                            preset = preset,
                            onComplete = { model ->
                                onFinishDownload(preset.id)
                                onClearError()
                                selectInstalledPiperModel(model)
                            },
                            onError = { error -> onError(preset.id, error) },
                        )
                    }
                },
                onDelete = {
                    onClearError()
                    deletePiperModel(preset.id)
                },
            )
        }

        customModels.forEach { customModel ->
            PiperModelItemRow(
                name = customModel.name,
                installedModel = customModel,
                isSelected = activeModelId == customModel.id,
                downloadProgress = downloadProgress[customModel.id],
                isPendingDownload = customModel.id in pendingDownloads,
                downloadSupported = PiperOnnxEngine.isSupported,
                onSelect = { model ->
                    onClearError()
                    selectInstalledPiperModel(model)
                },
                onPreview = { model ->
                    onClearError()
                    SubtitleTtsCoordinator.previewVoice(overrideModel = model)
                },
                onDownload = {},
                onDelete = {
                    onClearError()
                    deletePiperModel(customModel.id)
                },
            )
        }

        pendingCustomDownloads.forEach { (pendingId, pendingName) ->
            PiperModelItemRow(
                name = pendingName,
                installedModel = null,
                isSelected = false,
                downloadProgress = downloadProgress[pendingId],
                isPendingDownload = true,
                downloadSupported = PiperOnnxEngine.isSupported,
                onSelect = {},
                onPreview = {},
                onDownload = {},
                onDelete = {},
            )
        }
    }

    if (PiperOnnxEngine.isSupported) {
        SettingsGroupDivider(isTablet = isTablet)
        SettingsNavigationRow(
            title = "Thêm giọng từ link HuggingFace",
            description = "Tải model VITS ONNX tùy chỉnh từ kho HuggingFace",
            isTablet = isTablet,
            onClick = onOpenAddCustomDialog,
        )
    }
}

@Composable
private fun PiperModelItemRow(
    name: String,
    installedModel: InstalledPiperModel?,
    isSelected: Boolean,
    downloadProgress: Float?,
    isPendingDownload: Boolean,
    downloadSupported: Boolean,
    onSelect: (InstalledPiperModel) -> Unit,
    onPreview: (InstalledPiperModel) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isInstalled = installedModel != null
    val isBusy = downloadProgress != null || isPendingDownload
    val containerColor = if (isSelected) {
        tokens.colors.accent.copy(alpha = tokens.opacity.selected)
    } else {
        tokens.colors.surfaceCard
    }
    val statusText = when {
        downloadProgress != null -> "Đang tải: ${(downloadProgress * PERCENT_SCALE).roundToInt()}%"
        isPendingDownload -> "Đang chuẩn bị tải..."
        isSelected -> "Đang sử dụng"
        isInstalled -> "Đã tải · Sẵn sàng offline"
        !downloadSupported -> "Chỉ hỗ trợ Android"
        else -> "Chưa tải (~60.6 MB)"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapes.compactCard,
        color = containerColor,
        border = BorderStroke(
            width = tokens.borders.hairline,
            color = if (isSelected) tokens.colors.borderSelected else tokens.colors.borderSubtle,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(tokens.shapes.compactCard)
                    .selectable(
                        selected = isSelected,
                        enabled = isInstalled || (!isBusy && downloadSupported),
                        role = Role.RadioButton,
                        onClick = {
                            if (installedModel != null) {
                                onSelect(installedModel)
                            } else {
                                onDownload()
                            }
                        },
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) tokens.colors.accent else tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                        modifier = Modifier.size(tokens.icons.md),
                    )
                }
            }

            if (installedModel != null) {
                IconButton(
                    onClick = { onPreview(installedModel) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Nghe thử $name",
                        tint = tokens.colors.accent,
                        modifier = Modifier.size(tokens.icons.md),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Xóa $name",
                        tint = tokens.colors.danger,
                        modifier = Modifier.size(tokens.icons.md),
                    )
                }
            } else if (isBusy) {
                NuvioLoadingIndicator(
                    size = tokens.icons.md,
                    color = tokens.colors.accent,
                    modifier = Modifier.padding(end = 6.dp),
                )
            } else if (downloadSupported) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Tải về $name",
                        tint = tokens.colors.accent,
                        modifier = Modifier.size(tokens.icons.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsStatusBanner(
    message: String,
    isWarning: Boolean,
) {
    val tokens = MaterialTheme.nuvio
    val tint = if (isWarning) tokens.colors.warning else tokens.colors.info
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapes.compactCard,
        color = tint.copy(alpha = tokens.opacity.hover),
        border = BorderStroke(tokens.borders.hairline, tint.copy(alpha = tokens.opacity.medium)),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TtsErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapes.compactCard,
        color = tokens.colors.danger.copy(alpha = tokens.opacity.selected),
        border = BorderStroke(tokens.borders.thin, tokens.colors.danger.copy(alpha = tokens.opacity.strong)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Đóng thông báo lỗi",
                    tint = tokens.colors.danger,
                    modifier = Modifier.size(tokens.icons.sm),
                )
            }
        }
    }
}

@Composable
private fun GeneralTtsAudioRows(
    isTablet: Boolean,
    ttsSettings: SubtitleTtsSettings,
) {
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSliderRow(
        title = "Âm lượng phim gốc",
        value = (ttsSettings.movieVolume * PERCENT_SCALE).roundToInt(),
        valueText = { "$it%" },
        valueRange = VOLUME_MIN_PERCENT..VOLUME_MAX_PERCENT,
        step = PERCENT_STEP,
        isTablet = isTablet,
        onValueChange = { volPercent ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(movieVolume = volPercent / PERCENT_SCALE)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSliderRow(
        title = "Âm lượng giọng đọc",
        value = (ttsSettings.ttsVolume * PERCENT_SCALE).roundToInt(),
        valueText = { "$it%" },
        valueRange = VOLUME_MIN_PERCENT..VOLUME_MAX_PERCENT,
        step = PERCENT_STEP,
        isTablet = isTablet,
        onValueChange = { volPercent ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(ttsVolume = volPercent / PERCENT_SCALE)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSliderRow(
        title = "Tốc độ đọc cơ bản",
        value = (ttsSettings.baseSpeed * PERCENT_SCALE).roundToInt(),
        valueText = { formatSpeedX(it) },
        valueRange = SPEED_MIN_PERCENT..SPEED_MAX_PERCENT,
        step = PERCENT_STEP,
        isTablet = isTablet,
        onValueChange = { speedPercent ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(baseSpeed = speedPercent / PERCENT_SCALE)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = "Tự động căn nhịp theo thời gian phụ đề",
        description = "Tự động tính tốc độ đọc từ mốc thời gian trong file phụ đề để đọc hết câu đúng nhịp phim",
        checked = ttsSettings.autoSpeedEnabled,
        isTablet = isTablet,
        onCheckedChange = { autoSpeed ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(autoSpeedEnabled = autoSpeed)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSliderRow(
        title = "Tốc độ tối đa khi bắt nhịp",
        value = (ttsSettings.maxAutoSpeed * PERCENT_SCALE)
            .roundToInt()
            .coerceIn(MAX_AUTO_SPEED_MIN_PERCENT, MAX_AUTO_SPEED_MAX_PERCENT),
        valueText = { formatSpeedX(it) },
        valueRange = MAX_AUTO_SPEED_MIN_PERCENT..MAX_AUTO_SPEED_MAX_PERCENT,
        step = PERCENT_STEP,
        isTablet = isTablet,
        enabled = ttsSettings.autoSpeedEnabled,
        onValueChange = { speedPercent ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(
                    maxAutoSpeed = (speedPercent / PERCENT_SCALE).coerceIn(
                        SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED,
                        SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
                    ),
                )
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = "Chờ đọc hết câu trước khi chuyển",
        description = "Nếu câu trước còn dở khi sang câu mới, chờ đọc nốt (trong ngưỡng an toàn) rồi mới chuyển tiếp",
        checked = ttsSettings.waitForCueCompletion,
        isTablet = isTablet,
        onCheckedChange = { wait ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(waitForCueCompletion = wait)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = "Phiên âm tên riêng & từ tiếng Anh",
        description = "Đọc tự nhiên tên nhân vật, địa danh và thuật ngữ tiếng Anh theo ngữ âm tiếng Việt",
        checked = ttsSettings.transliterateEnglishNames,
        isTablet = isTablet,
        onCheckedChange = { transliterate ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(transliterateEnglishNames = transliterate)
            }
        },
    )

    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = "Ẩn phụ đề khi TTS đọc",
        description = "Chỉ ẩn hiển thị chữ trên màn hình, phụ đề vẫn chạy ngầm để TTS đọc",
        checked = ttsSettings.hideSubtitlesWhenEnabled,
        isTablet = isTablet,
        onCheckedChange = { hideSubs ->
            SubtitleTtsCoordinator.updateSettings {
                it.copy(hideSubtitlesWhenEnabled = hideSubs)
            }
        },
    )
}

/**
 * Formats an integer speed percentage (e.g. `210`) into a clean `x` multiplier label (e.g. `2.1x`).
 */
private fun formatSpeedX(speedPercent: Int): String {
    val hundredths = speedPercent.coerceAtLeast(0)
    val scale = PERCENT_SCALE.roundToInt()
    val whole = hundredths / scale
    val frac = hundredths % scale
    return if (frac % DECIMAL_BASE == 0) {
        "$whole.${frac / DECIMAL_BASE}x"
    } else {
        "$whole.${frac.toString().padStart(2, '0')}x"
    }
}

@Composable
private fun SystemVoiceSelectionDialog(
    voices: List<SystemVoiceInfo>,
    selectedVoiceName: String?,
    onSelect: (String?) -> Unit,
    onPreview: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Chọn Giọng đọc hệ thống",
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = VOICE_DIALOG_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__default_vi__") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DialogOption(
                        text = "Mặc định (Tự động chọn Tiếng Việt)",
                        description = "Tự ưu tiên giọng Tiếng Việt offline tốt nhất trên máy",
                        selected = selectedVoiceName == null,
                        onClick = { onSelect(null) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onPreview(null) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Nghe thử giọng mặc định",
                            tint = tokens.colors.accent,
                        )
                    }
                }
            }
            items(voices, key = { it.name }) { voice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DialogOption(
                        text = voice.displayName,
                        description = "Mã: ${voice.name} | Ngôn ngữ: ${voice.locale}",
                        selected = voice.name == selectedVoiceName,
                        onClick = { onSelect(voice.name) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onPreview(voice.name) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Nghe thử ${voice.displayName}",
                            tint = tokens.colors.accent,
                        )
                    }
                }
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun AddCustomPiperModelDialog(
    onDownload: (name: String, onnxUrl: String, jsonUrl: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var customName by remember { mutableStateOf("") }
    var customOnnxUrl by remember { mutableStateOf("") }
    var customJsonUrl by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
        unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
        focusedContainerColor = tokens.colors.surface,
        unfocusedContainerColor = tokens.colors.surface,
        disabledContainerColor = tokens.colors.surface,
    )

    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Thêm giọng từ HuggingFace",
    ) {
        Text(
            text = "Nhập link file model .onnx và .onnx.json trên HuggingFace (hệ thống tự chuyển /blob/ thành /resolve/):",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textSecondary,
        )
        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it },
            label = { Text("Tên hiển thị (vd: Giọng Nữ VIP)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )
        OutlinedTextField(
            value = customOnnxUrl,
            onValueChange = {
                customOnnxUrl = it
                validationError = null
            },
            label = { Text("Link tải .onnx") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )
        OutlinedTextField(
            value = customJsonUrl,
            onValueChange = {
                customJsonUrl = it
                validationError = null
            },
            label = { Text("Link tải .onnx.json (tùy chọn)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
        )
        if (validationError != null) {
            Text(
                text = validationError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                style = DialogButtonStyle.Secondary,
                onClick = onDismiss,
            )
            DialogButton(
                text = "Tải về",
                style = DialogButtonStyle.Primary,
                onClick = {
                    val trimmedName = customName.trim().ifBlank { "Custom Piper Voice" }
                    val trimmedOnnx = customOnnxUrl.trim()
                    val trimmedJson = customJsonUrl.trim()
                    val isOnnxValid = trimmedOnnx.startsWith("http://", ignoreCase = true) ||
                        trimmedOnnx.startsWith("https://", ignoreCase = true)
                    val isJsonValid = trimmedJson.isBlank() ||
                        trimmedJson.startsWith("http://", ignoreCase = true) ||
                        trimmedJson.startsWith("https://", ignoreCase = true)
                    when {
                        !isOnnxValid -> validationError = "Vui lòng nhập link .onnx hợp lệ (http:// hoặc https://)."
                        !isJsonValid -> validationError = "Link .onnx.json phải bắt đầu bằng http:// hoặc https://."
                        else -> {
                            onDownload(trimmedName, trimmedOnnx, trimmedJson)
                            onDismiss()
                        }
                    }
                },
            )
        }
    }
}

private fun selectInstalledPiperModel(model: InstalledPiperModel) {
    SubtitleTtsCoordinator.updateSettings { current ->
        val existing = current.installedPiperModels.filterNot { it.id == model.id }
        current.copy(
            installedPiperModels = existing + model,
            activePiperModelId = model.id,
        )
    }
}

private fun deletePiperModel(modelId: String) {
    PiperModelManager.deleteModel(modelId)
    SubtitleTtsCoordinator.updateSettings { current ->
        val updatedList = current.installedPiperModels.filterNot { it.id == modelId }
        val newActive = if (current.activePiperModelId == modelId) {
            updatedList.firstOrNull()?.id
        } else {
            current.activePiperModelId
        }
        current.copy(
            installedPiperModels = updatedList,
            activePiperModelId = newActive,
        )
    }
}
