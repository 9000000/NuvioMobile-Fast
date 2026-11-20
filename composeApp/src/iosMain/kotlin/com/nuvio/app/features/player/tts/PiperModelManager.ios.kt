package com.nuvio.app.features.player.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object PiperModelManager {
    actual val presets: List<PiperPreset> = listOf(
        PiperPreset(
            id = "ngochuyen5",
            name = "Ngọc Huyền 5 (Piper AI)",
            onnxUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx",
            jsonUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx.json",
            sampleRate = 22050,
        ),
        PiperPreset(
            id = "lacphi",
            name = "Lạc Phi (Piper AI)",
            onnxUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/L%E1%BA%A1c%20Phi%E2%9C%93%E2%84%A2%C2%AE.onnx",
            jsonUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/L%E1%BA%A1c%20Phi%E2%9C%93%E2%84%A2%C2%AE.onnx.json",
            sampleRate = 22050,
        ),
        PiperPreset(
            id = "ngochuyen",
            name = "Ngọc Huyền (Piper AI)",
            onnxUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/Ng%E1%BB%8Dc%20Huy%E1%BB%81n%E2%84%A2%C2%AE.onnx",
            jsonUrl = "https://huggingface.co/hoanglinhn0/Model/resolve/main/Ng%E1%BB%8Dc%20Huy%E1%BB%81n%E2%84%A2%C2%AE.onnx.json",
            sampleRate = 22050,
        ),
        PiperPreset(
            id = "vais1000",
            name = "Giọng Nữ Chuẩn (VAIS1000)",
            onnxUrl = "https://huggingface.co/csukuangfj/vits-piper-vi_VN-vais1000-medium/resolve/main/vi_VN-vais1000-medium.onnx",
            jsonUrl = "https://huggingface.co/csukuangfj/vits-piper-vi_VN-vais1000-medium/resolve/main/vi_VN-vais1000-medium.onnx.json",
            sampleRate = 22050,
        ),
    )

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    actual val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isEspeakDownloading = MutableStateFlow(false)
    actual val isEspeakDownloading: StateFlow<Boolean> = _isEspeakDownloading.asStateFlow()

    private val _espeakDownloadProgress = MutableStateFlow(0f)
    actual val espeakDownloadProgress: StateFlow<Float> = _espeakDownloadProgress.asStateFlow()

    actual fun isModelInstalled(modelId: String): Boolean = false
    actual fun getInstalledModel(modelId: String): InstalledPiperModel? = null
    actual fun downloadPreset(
        preset: PiperPreset,
        onComplete: (InstalledPiperModel) -> Unit,
        onError: (String) -> Unit,
    ) {
        onError("Piper ONNX chưa hỗ trợ trên iOS")
    }

    actual fun downloadCustomModel(
        id: String,
        name: String,
        onnxUrl: String,
        jsonUrl: String,
        onComplete: (InstalledPiperModel) -> Unit,
        onError: (String) -> Unit,
    ) {
        onError("Piper ONNX chưa hỗ trợ trên iOS")
    }

    actual fun deleteModel(modelId: String) {}
    actual fun getEspeakDir(): String = ""
}
