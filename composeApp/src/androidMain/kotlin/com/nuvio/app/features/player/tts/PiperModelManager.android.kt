package com.nuvio.app.features.player.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

actual object PiperModelManager {
    private const val TAG = "PiperModelManager"
    private const val MIN_VALID_ONNX_BYTES = 1_000_000L
    private const val ESPEAK_DATA_URL = "https://huggingface.co/anujpatel863/Separate/resolve/main/espeak-ng-data.zip"

    private val REQUIRED_ESPEAK_FILES = listOf(
        "phontab",
        "phonindex",
        "phondata",
        "intonations",
        "vi_dict",
        "lang/aav/vi",
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val espeakMutex = Mutex()
    private val espeakFsLock = Any()
    private var appContext: Context? = null

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
    )

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    actual val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isEspeakDownloading = MutableStateFlow(false)
    actual val isEspeakDownloading: StateFlow<Boolean> = _isEspeakDownloading.asStateFlow()

    private val _espeakDownloadProgress = MutableStateFlow(0f)
    actual val espeakDownloadProgress: StateFlow<Float> = _espeakDownloadProgress.asStateFlow()

    private fun sanitizeId(modelId: String): String =
        modelId.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }

    private fun resolveContext(): Context? {
        appContext?.let { return it }
        val context = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        if (context != null) {
            appContext = context.applicationContext
        }
        return appContext
    }

    private fun getModelsRoot(): File {
        val ctx = resolveContext()
        val baseDir = ctx?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val dir = File(baseDir, "tts_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun mergeDirectoryUp(sourceDir: File, targetDir: File) {
        if (!sourceDir.isDirectory) return
        if (!targetDir.exists()) targetDir.mkdirs()
        val children = sourceDir.listFiles() ?: return
        for (child in children) {
            val dest = File(targetDir, child.name)
            if (child.isDirectory) {
                mergeDirectoryUp(child, dest)
                child.delete()
            } else {
                if (!dest.exists() || dest.length() == 0L) {
                    if (dest.exists()) dest.delete()
                    if (!child.renameTo(dest)) {
                        child.copyTo(dest, overwrite = true)
                        child.delete()
                    }
                } else {
                    child.delete()
                }
            }
        }
    }

    private fun flattenNestedEspeakIfNeeded(baseDir: File) {
        synchronized(espeakFsLock) {
            var nested = File(baseDir, "espeak-ng-data")
            while (nested.isDirectory) {
                Log.w(TAG, "Flattening nested espeak-ng-data directory: ${nested.absolutePath} -> ${baseDir.absolutePath}")
                val tempHold = File(baseDir.parentFile ?: baseDir, "espeak-ng-data-flatten-${System.nanoTime()}")
                if (nested.renameTo(tempHold)) {
                    mergeDirectoryUp(tempHold, baseDir)
                    tempHold.deleteRecursively()
                } else {
                    // Fallback: copy nested directory outside first if direct self-named merge hits edge case
                    mergeDirectoryUp(nested, baseDir)
                    nested.deleteRecursively()
                }
                nested = File(baseDir, "espeak-ng-data")
            }
        }
    }

    internal fun hasAllRequiredEspeakFiles(dir: File): Boolean =
        dir.isDirectory && REQUIRED_ESPEAK_FILES.all { rel ->
            val f = File(dir, rel)
            f.isFile && f.length() > 0L
        }

    actual fun getEspeakDir(): String {
        val ctx = resolveContext()
        val baseDir = ctx?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val dir = File(baseDir, "espeak-ng-data")
        if (!dir.exists()) dir.mkdirs()
        flattenNestedEspeakIfNeeded(dir)
        return dir.absolutePath
    }

    private fun isEspeakReady(): Boolean {
        val espeakDir = File(getEspeakDir())
        return hasAllRequiredEspeakFiles(espeakDir)
    }

    internal fun ensureEspeakDataSync(): Boolean {
        if (isEspeakReady()) return true
        return runCatching {
            runBlocking {
                ensureEspeakData()
            }
            isEspeakReady()
        }.onFailure { e ->
            Log.e(TAG, "Failed to ensure espeak-ng-data synchronously", e)
        }.getOrDefault(false)
    }

    actual fun isModelInstalled(modelId: String): Boolean {
        val safeId = sanitizeId(modelId)
        if (safeId.isBlank()) return false
        val dir = File(getModelsRoot(), safeId)
        val onnx = File(dir, "model.onnx")
        val tokens = File(dir, "tokens.txt")
        return onnx.isFile && onnx.length() > MIN_VALID_ONNX_BYTES && tokens.isFile && tokens.length() > 0L
    }

    actual fun getInstalledModel(modelId: String): InstalledPiperModel? {
        val safeId = sanitizeId(modelId)
        if (!isModelInstalled(safeId)) return null
        val dir = File(getModelsRoot(), safeId)
        val preset = presets.firstOrNull { it.id == modelId }
        val savedName = File(dir, "model_name.txt").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }
        val jsonConfig = File(dir, "model.onnx.json").takeIf { it.isFile }?.let { jsonFile ->
            runCatching { PiperConfigParser.parseConfig(jsonFile.readText()) }.getOrNull()
        }
        return InstalledPiperModel(
            id = modelId,
            name = preset?.name ?: savedName ?: modelId,
            dirPath = dir.absolutePath,
            sampleRate = jsonConfig?.sampleRate ?: preset?.sampleRate ?: 22050,
        )
    }

    actual fun downloadPreset(
        preset: PiperPreset,
        onComplete: (InstalledPiperModel) -> Unit,
        onError: (String) -> Unit,
    ) {
        downloadCustomModel(
            id = preset.id,
            name = preset.name,
            onnxUrl = preset.onnxUrl,
            jsonUrl = preset.jsonUrl,
            onComplete = onComplete,
            onError = onError,
        )
    }

    actual fun downloadCustomModel(
        id: String,
        name: String,
        onnxUrl: String,
        jsonUrl: String,
        onComplete: (InstalledPiperModel) -> Unit,
        onError: (String) -> Unit,
    ) {
        val safeId = sanitizeId(id)
        if (safeId.isBlank()) {
            onError("Mã định danh không hợp lệ")
            return
        }

        scope.launch {
            try {
                if (!isEspeakReady()) {
                    ensureEspeakData()
                }

                val targetDir = File(getModelsRoot(), safeId)
                if (!targetDir.exists()) targetDir.mkdirs()

                val resolvedJsonUrl = PiperConfigParser.inferJsonUrl(jsonUrl.ifBlank { onnxUrl })
                val jsonRequest = Request.Builder().url(resolvedJsonUrl).build()
                val jsonContent = httpClient.newCall(jsonRequest).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Không thể tải cấu hình JSON (${resp.code})")
                    resp.body?.string().orEmpty()
                }

                val parsed = PiperConfigParser.parseConfig(jsonContent)
                if (parsed.tokensText.isBlank()) {
                    throw IllegalStateException("File JSON không chứa bảng phoneme_id_map hợp lệ")
                }

                File(targetDir, "model_name.txt").writeText(name)
                File(targetDir, "model.onnx.json").writeText(jsonContent)
                File(targetDir, "tokens.txt").writeText(parsed.tokensText)

                val resolvedOnnxUrl = PiperConfigParser.normalizeHuggingFaceUrl(onnxUrl)
                val onnxRequest = Request.Builder().url(resolvedOnnxUrl).build()
                val onnxFile = File(targetDir, "model.onnx")
                val tempFile = File(targetDir, "model.onnx.tmp")

                _downloadProgress.value = _downloadProgress.value + (safeId to 0f)
                httpClient.newCall(onnxRequest).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Không thể tải file model ONNX (${resp.code})")
                    val body = resp.body ?: throw IllegalStateException("Body trống")
                    val totalLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var readBytes = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                readBytes += read
                                if (totalLength > 0) {
                                    val progress = (readBytes.toFloat() / totalLength).coerceIn(0f, 1f)
                                    _downloadProgress.value = _downloadProgress.value + (safeId to progress)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.exists()) {
                    if (onnxFile.exists()) onnxFile.delete()
                    if (!tempFile.renameTo(onnxFile)) {
                        tempFile.copyTo(onnxFile, overwrite = true)
                        tempFile.delete()
                    }
                }

                if (!onnxFile.isFile || onnxFile.length() <= MIN_VALID_ONNX_BYTES) {
                    onnxFile.delete()
                    throw IllegalStateException("File model ONNX tải về bị lỗi hoặc không đủ dung lượng")
                }

                // Inject Protobuf metadata in-place so sherpa-onnx never crashes on raw Piper models
                val metadataOk = OnnxMetadataInjector.ensureMetadata(
                    onnxFile = onnxFile,
                    sampleRate = parsed.sampleRate,
                    voice = parsed.espeakVoice,
                    nSpeakers = parsed.numSpeakers,
                )
                if (!metadataOk) {
                    throw IllegalStateException("Không thể gắn metadata Protobuf vào file ONNX")
                }

                _downloadProgress.value = _downloadProgress.value - safeId
                Log.i(TAG, "Downloaded Piper model '$safeId' (${onnxFile.length()} bytes, sr=${parsed.sampleRate}, voice=${parsed.espeakVoice})")

                val installed = InstalledPiperModel(
                    id = safeId,
                    name = name,
                    dirPath = targetDir.absolutePath,
                    sampleRate = parsed.sampleRate,
                )
                withContext(Dispatchers.Main) {
                    onComplete(installed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading Piper model '$safeId'", e)
                _downloadProgress.value = _downloadProgress.value - safeId
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Lỗi tải model")
                }
            }
        }
    }

    private suspend fun ensureEspeakData() {
        espeakMutex.withLock {
            val espeakDir = File(getEspeakDir())
            if (hasAllRequiredEspeakFiles(espeakDir)) return

            _isEspeakDownloading.value = true
            _espeakDownloadProgress.value = 0f

            val tempZip = File(espeakDir.parentFile ?: espeakDir, "espeak-ng-data.zip.tmp")
            try {
                val req = Request.Builder().url(ESPEAK_DATA_URL).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Lỗi tải espeak-ng-data (${resp.code})")
                    val body = resp.body ?: throw IllegalStateException("Empty body")
                    val totalLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var readBytes = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                readBytes += read
                                if (totalLength > 0) {
                                    _espeakDownloadProgress.value = (readBytes.toFloat() / totalLength).coerceIn(0f, 1f)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                // Unzip flatly into espeakDir with ZipSlip protection
                val canonicalRoot = espeakDir.canonicalFile
                tempZip.inputStream().use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            var cleanName = entry.name.replace('\\', '/').trimStart('/')
                            while (cleanName.startsWith("espeak-ng-data/")) {
                                cleanName = cleanName.removePrefix("espeak-ng-data/")
                            }
                            if (cleanName.isNotEmpty()) {
                                val outFile = File(canonicalRoot, cleanName)
                                val canonicalOut = outFile.canonicalFile
                                if (!canonicalOut.path.startsWith(canonicalRoot.path + File.separator) && canonicalOut != canonicalRoot) {
                                    throw SecurityException("Invalid zip entry path: ${entry.name}")
                                }
                                if (entry.isDirectory) {
                                    canonicalOut.mkdirs()
                                } else {
                                    canonicalOut.parentFile?.mkdirs()
                                    FileOutputStream(canonicalOut, false).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                flattenNestedEspeakIfNeeded(espeakDir)
                if (!hasAllRequiredEspeakFiles(espeakDir)) {
                    val missing = REQUIRED_ESPEAK_FILES.filterNot { File(espeakDir, it).isFile }
                    throw IllegalStateException("Dữ liệu espeak-ng-data thiếu tệp: ${missing.joinToString()}")
                }
                Log.i(TAG, "espeak-ng-data ready at ${espeakDir.absolutePath}")
            } finally {
                tempZip.delete()
                _isEspeakDownloading.value = false
                _espeakDownloadProgress.value = 1f
            }
        }
    }

    actual fun deleteModel(modelId: String) {
        val safeId = sanitizeId(modelId)
        if (safeId.isBlank()) return
        val dir = File(getModelsRoot(), safeId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
