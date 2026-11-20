package com.nuvio.app.features.player.tts

import android.os.Build
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

actual object PiperOnnxEngine {
    private const val TAG = "PiperOnnxEngine"
    private const val MIN_VALID_ONNX_BYTES = 1024L
    private val BUNDLED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    private val engineLock = Any()
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var loadedModelId: String? = null
    private var nativeLoadAttempted: Boolean = false
    private var nativeLoadSucceeded: Boolean = false

    actual val isSupported: Boolean
        get() {
            val hasAbi = runCatching {
                Build.SUPPORTED_ABIS.any { it in BUNDLED_ABIS }
            }.getOrDefault(false)
            if (!hasAbi) return false
            return synchronized(engineLock) {
                !nativeLoadAttempted || nativeLoadSucceeded
            }
        }

    actual val isModelLoaded: Boolean
        get() = synchronized(engineLock) { tts != null }

    actual val currentLoadedModelId: String?
        get() = synchronized(engineLock) { loadedModelId }

    private fun ensureNativeLoadedLocked(): Boolean {
        if (nativeLoadAttempted) return nativeLoadSucceeded
        nativeLoadAttempted = true
        return try {
            System.loadLibrary("sherpa-onnx-jni")
            nativeLoadSucceeded = true
            val abis = runCatching { Build.SUPPORTED_ABIS.joinToString() }.getOrDefault("unknown")
            Log.i(TAG, "Loaded sherpa-onnx-jni native library successfully (ABIs=$abis)")
            true
        } catch (t: Throwable) {
            nativeLoadSucceeded = false
            val abis = runCatching { Build.SUPPORTED_ABIS.joinToString() }.getOrDefault("unknown")
            Log.e(TAG, "Failed loading sherpa-onnx-jni native library (ABIs=$abis)", t)
            false
        }
    }

    private fun resolveValidEspeakDir(): File? {
        val rawEspeakDir = File(PiperModelManager.getEspeakDir())
        return when {
            PiperModelManager.hasAllRequiredEspeakFiles(rawEspeakDir) -> rawEspeakDir
            PiperModelManager.hasAllRequiredEspeakFiles(File(rawEspeakDir, "espeak-ng-data")) -> {
                File(rawEspeakDir, "espeak-ng-data")
            }
            else -> null
        }
    }

    actual fun loadModel(model: InstalledPiperModel): Boolean {
        synchronized(engineLock) {
            if (loadedModelId == model.id && tts != null) return true
            releaseInternalLocked()

            if (!ensureNativeLoadedLocked()) {
                return false
            }

            val modelDir = File(model.dirPath)
            val onnxFile = File(modelDir, "model.onnx")
            val tokensFile = File(modelDir, "tokens.txt")

            if (!onnxFile.isFile || onnxFile.length() <= MIN_VALID_ONNX_BYTES ||
                !tokensFile.isFile || tokensFile.length() == 0L
            ) {
                Log.e(
                    TAG,
                    "Missing or invalid model files for '${model.id}' at ${modelDir.absolutePath} " +
                        "(onnx=${onnxFile.isFile}:${onnxFile.length()}, tokens=${tokensFile.isFile}:${tokensFile.length()})",
                )
                return false
            }

            var validEspeakDir = resolveValidEspeakDir()
            if (validEspeakDir == null) {
                Log.w(TAG, "espeak-ng-data missing/incomplete at ${PiperModelManager.getEspeakDir()}; attempting self-heal")
                if (PiperModelManager.ensureEspeakDataSync()) {
                    validEspeakDir = resolveValidEspeakDir()
                }
            }
            if (validEspeakDir == null) {
                Log.e(TAG, "espeak-ng-data not ready at ${PiperModelManager.getEspeakDir()}; aborting OfflineTts init")
                return false
            }

            val jsonFile = File(modelDir, "model.onnx.json")
            val parsedConfig = if (jsonFile.isFile) {
                runCatching { PiperConfigParser.parseConfig(jsonFile.readText()) }
                    .onFailure { Log.w(TAG, "Failed parsing ${jsonFile.absolutePath}; using fallback config", it) }
                    .getOrNull()
            } else {
                null
            }

            // Guarantee ONNX model has required Protobuf metadata (sample_rate, model_type, etc.)
            val metadataReady = OnnxMetadataInjector.ensureMetadata(
                onnxFile = onnxFile,
                sampleRate = parsedConfig?.sampleRate ?: model.sampleRate,
                voice = parsedConfig?.espeakVoice ?: "vi",
                nSpeakers = parsedConfig?.numSpeakers ?: 1,
            )
            if (!metadataReady) {
                Log.e(TAG, "Failed ensuring ONNX Protobuf metadata for ${onnxFile.absolutePath}")
                return false
            }

            return try {
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = onnxFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = validEspeakDir.absolutePath,
                    noiseScale = parsedConfig?.noiseScale ?: 0.667f,
                    noiseScaleW = parsedConfig?.noiseW ?: 0.8f,
                    lengthScale = parsedConfig?.lengthScale ?: 1.0f,
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                )

                val config = OfflineTtsConfig(
                    model = modelConfig,
                    silenceScale = 0.7f,
                )

                val createdTts = OfflineTts(assetManager = null, config = config)
                tts = createdTts
                loadedModelId = model.id
                Log.i(
                    TAG,
                    "Initialized Piper OfflineTts '${model.id}' " +
                        "(sampleRate=${createdTts.sampleRate()}, speakers=${createdTts.numSpeakers()}, dataDir=${validEspeakDir.absolutePath})",
                )
                true
            } catch (t: Throwable) {
                Log.e(
                    TAG,
                    "Failed initializing OfflineTts for '${model.id}' " +
                        "(onnx=${onnxFile.absolutePath}, tokens=${tokensFile.absolutePath}, dataDir=${validEspeakDir.absolutePath})",
                    t,
                )
                releaseInternalLocked()
                false
            }
        }
    }

    actual fun generatePcm(text: String, speed: Float): FloatArray? {
        if (text.isBlank()) return null
        synchronized(engineLock) {
            val currentTts = tts ?: return null
            val safeSpeed = speed.coerceIn(0.5f, 2.5f)
            return try {
                val audio = currentTts.generate(text = text, sid = 0, speed = safeSpeed)
                val samples = audio.samples
                if (samples.isEmpty()) {
                    Log.w(TAG, "OfflineTts.generate returned 0 samples for model='$loadedModelId'")
                    null
                } else {
                    samples
                }
            } catch (t: Throwable) {
                Log.e(TAG, "OfflineTts.generate threw exception for model='$loadedModelId'", t)
                null
            }
        }
    }

    private fun releaseInternalLocked() {
        val current = tts
        tts = null
        loadedModelId = null
        if (current != null) {
            runCatching {
                current.release()
            }.onFailure { t ->
                Log.w(TAG, "OfflineTts.release failed", t)
            }
        }
    }

    actual fun release() {
        synchronized(engineLock) {
            releaseInternalLocked()
        }
    }
}
