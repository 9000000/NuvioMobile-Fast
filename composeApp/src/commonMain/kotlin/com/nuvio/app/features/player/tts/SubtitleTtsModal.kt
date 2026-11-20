package com.nuvio.app.features.player.tts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Slider
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.player.PlayerOverlayScaffold
import kotlin.math.roundToInt

private const val PERCENT_SCALE = 100f
private const val MIN_SPEED = 0.8f
private const val MAX_SPEED = 1.5f
private const val MIN_PITCH = 0.6f
private const val MAX_PITCH = 1.5f
private const val AUTO_SPEED_STEP_PERCENT = 5
private const val DECIMAL_BASE = 10
private val AUTO_SPEED_SLIDER_STEPS = (
    (
        (SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED - SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED) *
            PERCENT_SCALE / AUTO_SPEED_STEP_PERCENT
        ).roundToInt() - 1
    ).coerceAtLeast(0)
private const val MAX_VI_SYSTEM_VOICES = 4
private const val MAX_FALLBACK_SYSTEM_VOICES = 3

@Composable
fun SubtitleTtsModal(
    visible: Boolean,
    settings: SubtitleTtsSettings,
    onSettingsChange: (SubtitleTtsSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(visible) {
        if (visible) {
            SubtitleTtsCoordinator.refreshInstalledPiperModels()
        }
    }

    val tokens = MaterialTheme.nuvio
    val availableVoices by SystemTtsEngine.availableVoices.collectAsStateWithLifecycle()
    val coordinatorStatusMessage by SubtitleTtsCoordinator.statusMessage.collectAsStateWithLifecycle()

    val installedModels = remember(settings.installedPiperModels, visible) {
        val diskPresetModels = PiperModelManager.presets.mapNotNull { preset ->
            PiperModelManager.getInstalledModel(preset.id)
        }
        (settings.installedPiperModels + diskPresetModels).distinctBy { it.id }
    }
    val effectiveActiveModel = remember(installedModels, settings.activePiperModelId) {
        installedModels.firstOrNull { it.id == settings.activePiperModelId }
            ?: installedModels.firstOrNull()
    }

    LaunchedEffect(visible, installedModels, settings.activePiperModelId) {
        if (!visible) return@LaunchedEffect
        val missing = installedModels.filter { candidate ->
            settings.installedPiperModels.none { it.id == candidate.id }
        }
        val needsActive = installedModels.isNotEmpty() &&
            (settings.activePiperModelId == null || installedModels.none { it.id == settings.activePiperModelId })
        if (missing.isNotEmpty() || needsActive) {
            val nextActive = settings.activePiperModelId
                ?.takeIf { id -> installedModels.any { it.id == id } }
                ?: installedModels.firstOrNull()?.id
            onSettingsChange(
                settings.copy(
                    installedPiperModels = (settings.installedPiperModels + missing).distinctBy { it.id },
                    activePiperModelId = nextActive,
                ),
            )
        }
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val railWidth = minOf(maxWidth, 460.dp)

            Column(
                modifier = Modifier
                    .width(railWidth)
                    .heightIn(max = maxHeight)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    // Consume taps on blank panel space so PlayerOverlayScaffold does not dismiss.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TtsModalHeader(
                    enabled = settings.enabled,
                    onToggle = { enabled -> onSettingsChange(settings.copy(enabled = enabled)) },
                )

                if (settings.enabled) {
                    EngineSwitcherRow(
                        selectedEngine = settings.engineType,
                        onSelect = { engineType ->
                            if (engineType == SubtitleTtsEngineType.PIPER_ONNX) {
                                onSettingsChange(
                                    settings.copy(
                                        engineType = engineType,
                                        installedPiperModels = installedModels,
                                        activePiperModelId = effectiveActiveModel?.id,
                                    ),
                                )
                            } else {
                                onSettingsChange(settings.copy(engineType = engineType))
                            }
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.colors.accent.copy(alpha = 0.16f))
                            .clickable(role = Role.Button) {
                                SubtitleTtsCoordinator.previewVoice(
                                    overrideModel = if (settings.engineType == SubtitleTtsEngineType.PIPER_ONNX) {
                                        effectiveActiveModel
                                    } else {
                                        null
                                    },
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = tokens.colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Nghe thử giọng đọc",
                            color = tokens.colors.accent,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (settings.engineType == SubtitleTtsEngineType.SYSTEM) {
                        SystemVoicesSection(
                            availableVoices = availableVoices,
                            selectedVoiceName = settings.systemVoiceName,
                            systemPitch = settings.systemPitch,
                            onSelectVoice = { voiceName ->
                                onSettingsChange(settings.copy(systemVoiceName = voiceName))
                            },
                            onPreviewVoice = { voiceName ->
                                SubtitleTtsCoordinator.previewVoice(overrideVoiceName = voiceName)
                            },
                            onPitchChange = { pitch ->
                                onSettingsChange(settings.copy(systemPitch = pitch))
                            },
                        )
                    } else {
                        PiperVoicesSection(
                            installedModels = installedModels,
                            activeModelId = effectiveActiveModel?.id,
                            statusMessage = coordinatorStatusMessage,
                            onSelectModel = { model ->
                                SubtitleTtsCoordinator.clearStatusMessage()
                                onSettingsChange(
                                    settings.copy(
                                        installedPiperModels = (settings.installedPiperModels + model).distinctBy { it.id },
                                        activePiperModelId = model.id,
                                    ),
                                )
                            },
                            onPreviewModel = { model ->
                                SubtitleTtsCoordinator.clearStatusMessage()
                                SubtitleTtsCoordinator.previewVoice(overrideModel = model)
                            },
                        )
                    }

                    SubtitleTtsSliderSection(
                        label = "Âm lượng phim gốc: ${(settings.movieVolume * PERCENT_SCALE).roundToInt()}%",
                        value = settings.movieVolume,
                        valueRange = 0f..1f,
                        onValueChange = { volume ->
                            onSettingsChange(settings.copy(movieVolume = volume.coerceIn(0f, 1f)))
                        },
                    )

                    SubtitleTtsSliderSection(
                        label = "Âm lượng giọng đọc: ${(settings.ttsVolume * PERCENT_SCALE).roundToInt()}%",
                        value = settings.ttsVolume,
                        valueRange = 0f..1f,
                        onValueChange = { volume ->
                            onSettingsChange(settings.copy(ttsVolume = volume.coerceIn(0f, 1f)))
                        },
                    )

                    SubtitleTtsSliderSection(
                        label = "Tốc độ đọc: ${formatSpeedX(settings.baseSpeed)}",
                        value = settings.baseSpeed,
                        valueRange = MIN_SPEED..MAX_SPEED,
                        onValueChange = { speed ->
                            onSettingsChange(settings.copy(baseSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)))
                        },
                    )

                    TtsModalToggleRow(
                        title = "Tự động căn nhịp theo thời gian phụ đề",
                        description = null,
                        checked = settings.autoSpeedEnabled,
                        onCheckedChange = { autoSpeed ->
                            onSettingsChange(settings.copy(autoSpeedEnabled = autoSpeed))
                        },
                    )

                    if (settings.autoSpeedEnabled) {
                        SubtitleTtsSliderSection(
                            label = "Tốc độ tối đa: ${formatSpeedX(settings.maxAutoSpeed)}",
                            value = settings.maxAutoSpeed,
                            valueRange = SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED..SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
                            steps = AUTO_SPEED_SLIDER_STEPS,
                            onValueChange = { speed ->
                                val snapped = snapAutoSpeed(speed)
                                if (snapped != settings.maxAutoSpeed) {
                                    onSettingsChange(settings.copy(maxAutoSpeed = snapped))
                                }
                            },
                        )
                    }

                    TtsModalToggleRow(
                        title = "Chờ đọc hết câu trước khi chuyển",
                        description = "Chờ đọc nốt câu dở (trong ngưỡng an toàn) rồi mới chuyển tiếp",
                        checked = settings.waitForCueCompletion,
                        onCheckedChange = { wait ->
                            onSettingsChange(settings.copy(waitForCueCompletion = wait))
                        },
                    )

                    TtsModalToggleRow(
                        title = "Ẩn phụ đề khi TTS đọc",
                        description = "Chỉ ẩn hiển thị chữ, phụ đề vẫn chạy ngầm để đọc",
                        checked = settings.hideSubtitlesWhenEnabled,
                        onCheckedChange = { hideSubs ->
                            onSettingsChange(settings.copy(hideSubtitlesWhenEnabled = hideSubs))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsModalHeader(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Thuyết minh phụ đề (TTS)",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.colors.onAccent,
                checkedTrackColor = tokens.colors.accent,
            ),
        )
    }
}

@Composable
private fun EngineSwitcherRow(
    selectedEngine: SubtitleTtsEngineType,
    onSelect: (SubtitleTtsEngineType) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubtitleTtsEngineType.entries.forEach { engineType ->
            val isSelected = selectedEngine == engineType
            val label = when (engineType) {
                SubtitleTtsEngineType.SYSTEM -> "Google / Hệ thống"
                SubtitleTtsEngineType.PIPER_ONNX -> "Piper AI (ONNX)"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) tokens.colors.accent else Color.White.copy(alpha = 0.12f),
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(engineType) },
                    )
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) tokens.colors.onAccent else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SystemVoicesSection(
    availableVoices: List<SystemVoiceInfo>,
    selectedVoiceName: String?,
    systemPitch: Float,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onPitchChange: (Float) -> Unit,
) {
    val viVoices = availableVoices.filter { it.locale.startsWith("vi", ignoreCase = true) }
    val displayVoices = if (viVoices.isNotEmpty()) {
        viVoices.take(MAX_VI_SYSTEM_VOICES)
    } else {
        availableVoices.take(MAX_FALLBACK_SYSTEM_VOICES)
    }

    if (displayVoices.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Giọng đọc Google Speech / Hệ thống",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
            )
            displayVoices.forEach { voice ->
                TtsVoiceSelectableRow(
                    label = voice.displayName,
                    isSelected = selectedVoiceName == voice.name,
                    previewDescription = "Nghe thử ${voice.displayName}",
                    onSelect = { onSelectVoice(voice.name) },
                    onPreview = { onPreviewVoice(voice.name) },
                )
            }
        }
    } else {
        Text(
            text = "Đang dùng giọng Tiếng Việt mặc định của hệ thống.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }

    SubtitleTtsSliderSection(
        label = "Cao độ giọng (Pitch): ${(systemPitch * PERCENT_SCALE).roundToInt()}%",
        value = systemPitch,
        valueRange = MIN_PITCH..MAX_PITCH,
        onValueChange = onPitchChange,
    )
}

@Composable
private fun PiperVoicesSection(
    installedModels: List<InstalledPiperModel>,
    activeModelId: String?,
    statusMessage: String?,
    onSelectModel: (InstalledPiperModel) -> Unit,
    onPreviewModel: (InstalledPiperModel) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    when {
        !PiperOnnxEngine.isSupported -> {
            TtsModalInfoBanner(
                text = "Piper ONNX chỉ hỗ trợ trên Android — đang dùng giọng hệ thống.",
                tint = tokens.colors.warning,
            )
        }
        installedModels.isEmpty() -> {
            TtsModalInfoBanner(
                text = "Chưa có giọng Piper AI nào được tải — đang tạm dùng Google Speech. Vào Cài đặt → Phát lại → Âm thanh để tải giọng Piper hoặc link HuggingFace.",
                tint = tokens.colors.warning,
            )
        }
        else -> {
            if (!statusMessage.isNullOrBlank()) {
                TtsModalInfoBanner(
                    text = statusMessage,
                    tint = tokens.colors.warning,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Giọng Piper AI đã tải",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
                installedModels.forEach { model ->
                    TtsVoiceSelectableRow(
                        label = model.name,
                        isSelected = activeModelId == model.id,
                        previewDescription = "Nghe thử ${model.name}",
                        onSelect = { onSelectModel(model) },
                        onPreview = { onPreviewModel(model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsVoiceSelectableRow(
    label: String,
    isSelected: Boolean,
    previewDescription: String,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) tokens.colors.accent.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f),
            )
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (isSelected) tokens.colors.accent else Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
        }
        IconButton(
            onClick = onPreview,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = previewDescription,
                tint = tokens.colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TtsModalInfoBanner(
    text: String,
    tint: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TtsModalToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.colors.onAccent,
                checkedTrackColor = tokens.colors.accent,
            ),
        )
    }
}

@Composable
private fun SubtitleTtsSliderSection(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/**
 * Snaps a raw slider speed value to the nearest 0.05x step within the allowed auto-speed range.
 */
private fun snapAutoSpeed(rawSpeed: Float): Float {
    val rawPercent = (rawSpeed * PERCENT_SCALE).roundToInt()
    val snappedPercent = (rawPercent.toFloat() / AUTO_SPEED_STEP_PERCENT).roundToInt() * AUTO_SPEED_STEP_PERCENT
    return (snappedPercent / PERCENT_SCALE).coerceIn(
        SubtitleTtsTimingCalculator.MIN_MAX_AUTO_SPEED,
        SubtitleTtsTimingCalculator.MAX_MAX_AUTO_SPEED,
    )
}

/**
 * Formats a speed multiplier (e.g. `2.1f`) into a deterministic `x` label without float artifacts.
 */
private fun formatSpeedX(speed: Float): String {
    val hundredths = (speed * PERCENT_SCALE).roundToInt().coerceAtLeast(0)
    val scale = PERCENT_SCALE.roundToInt()
    val whole = hundredths / scale
    val frac = hundredths % scale
    return if (frac % DECIMAL_BASE == 0) {
        "$whole.${frac / DECIMAL_BASE}x"
    } else {
        "$whole.${frac.toString().padStart(2, '0')}x"
    }
}
