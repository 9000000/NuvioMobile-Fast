package com.nuvio.app.features.player.tts

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Injects required metadata properties directly into an ONNX model file
 * using Protobuf Varint encoding so that sherpa-onnx can load raw Piper models
 * without crashing or complaining about missing sample_rate.
 */
object OnnxMetadataInjector {

    private const val TAG = "OnnxMetadataInjector"
    private const val MODEL_PROTO_METADATA_PROPS_TAG: Byte = 0x72 // (14 shl 3) or 2
    private const val ENTRY_KEY_TAG: Byte = 0x0a // (1 shl 3) or 2
    private const val ENTRY_VALUE_TAG: Byte = 0x12 // (2 shl 3) or 2
    private const val WINDOW_BYTES = 65536
    private const val OVERLAP_BYTES = 128
    private const val MIN_SAMPLE_RATE_ENTRY_BYTES = 16
    private const val MAX_SAMPLE_RATE_ENTRY_BYTES = 64

    private val SAMPLE_RATE_KEY_BYTES = "sample_rate".toByteArray(Charsets.UTF_8)

    private fun encodeVarint(value: Long): ByteArray {
        val bytes = ArrayList<Byte>()
        var v = value
        while (v > 0x7f) {
            bytes.add(((v and 0x7f) or 0x80).toByte())
            v = v ushr 7
        }
        bytes.add((v and 0x7f).toByte())
        return bytes.toByteArray()
    }

    private fun decodeVarint(bytes: ByteArray, start: Int, limit: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (index < limit && shift < 64) {
            val b = bytes[index].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            index++
            if ((b and 0x80) == 0) {
                return result to index
            }
            shift += 7
        }
        return null
    }

    private fun makeStringEntry(key: String, value: String): ByteArray {
        val kBytes = key.toByteArray(Charsets.UTF_8)
        val vBytes = value.toByteArray(Charsets.UTF_8)

        // StringStringEntryProto:
        // field 1 (key): tag (1 shl 3) or 2 = 10 (0x0a)
        // field 2 (value): tag (2 shl 3) or 2 = 18 (0x12)
        val entry = ByteArrayOutputStream()
        entry.write(ENTRY_KEY_TAG.toInt())
        entry.write(encodeVarint(kBytes.size.toLong()))
        entry.write(kBytes)
        entry.write(ENTRY_VALUE_TAG.toInt())
        entry.write(encodeVarint(vBytes.size.toLong()))
        entry.write(vBytes)
        val entryBytes = entry.toByteArray()

        // ModelProto:
        // field 14 (metadata_props): tag (14 shl 3) or 2 = 114 (0x72)
        val wrapper = ByteArrayOutputStream()
        wrapper.write(MODEL_PROTO_METADATA_PROPS_TAG.toInt())
        wrapper.write(encodeVarint(entryBytes.size.toLong()))
        wrapper.write(entryBytes)
        return wrapper.toByteArray()
    }

    private fun matchesKeyAt(bytes: ByteArray, offset: Int, limit: Int): Int? {
        if (offset + 2 + SAMPLE_RATE_KEY_BYTES.size > limit) return null
        if (bytes[offset] != ENTRY_KEY_TAG) return null
        val (keyLen, keyStart) = decodeVarint(bytes, offset + 1, limit) ?: return null
        if (keyLen != SAMPLE_RATE_KEY_BYTES.size.toLong()) return null
        val keyEnd = keyStart + SAMPLE_RATE_KEY_BYTES.size
        if (keyEnd > limit) return null
        for (i in SAMPLE_RATE_KEY_BYTES.indices) {
            if (bytes[keyStart + i] != SAMPLE_RATE_KEY_BYTES[i]) return null
        }
        return keyEnd
    }

    private fun isPositiveAsciiDigits(bytes: ByteArray, start: Int, end: Int): Boolean {
        if (end <= start || end - start > 10) return false
        var value = 0L
        for (i in start until end) {
            val c = bytes[i].toInt() and 0xff
            if (c < '0'.code || c > '9'.code) return false
            value = value * 10 + (c - '0'.code)
        }
        return value > 0L
    }

    internal fun containsSampleRateMetadataEntry(bytes: ByteArray, length: Int = bytes.size): Boolean {
        val limit = minOf(length, bytes.size)
        var i = 0
        while (i < limit) {
            if (bytes[i] == MODEL_PROTO_METADATA_PROPS_TAG) {
                val decodedEntry = decodeVarint(bytes, i + 1, limit)
                if (decodedEntry != null) {
                    val (entryLenLong, entryStart) = decodedEntry
                    if (entryLenLong in MIN_SAMPLE_RATE_ENTRY_BYTES..MAX_SAMPLE_RATE_ENTRY_BYTES) {
                        val entryEnd = entryStart + entryLenLong.toInt()
                        if (entryEnd <= limit) {
                            // Standard order: field 1 (key) then field 2 (value)
                            val afterKey = matchesKeyAt(bytes, entryStart, entryEnd)
                            if (afterKey != null && afterKey < entryEnd && bytes[afterKey] == ENTRY_VALUE_TAG) {
                                val valDecoded = decodeVarint(bytes, afterKey + 1, entryEnd)
                                if (valDecoded != null) {
                                    val (valLen, valStart) = valDecoded
                                    if (valLen in 1..10 && valStart + valLen.toInt() == entryEnd &&
                                        isPositiveAsciiDigits(bytes, valStart, entryEnd)
                                    ) {
                                        return true
                                    }
                                }
                            }
                            // Reverse order: field 2 (value) then field 1 (key)
                            if (entryStart < entryEnd && bytes[entryStart] == ENTRY_VALUE_TAG) {
                                val valDecoded = decodeVarint(bytes, entryStart + 1, entryEnd)
                                if (valDecoded != null) {
                                    val (valLen, valStart) = valDecoded
                                    val valEnd = valStart + valLen.toInt()
                                    if (valLen in 1..10 && valEnd < entryEnd &&
                                        isPositiveAsciiDigits(bytes, valStart, valEnd)
                                    ) {
                                        val afterKey = matchesKeyAt(bytes, valEnd, entryEnd)
                                        if (afterKey == entryEnd) {
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
        return false
    }

    private fun hasSampleRateMetadata(onnxFile: File): Boolean {
        val fileLength = onnxFile.length()
        if (fileLength < MIN_SAMPLE_RATE_ENTRY_BYTES + 2) return false

        RandomAccessFile(onnxFile, "r").use { raf ->
            // Fast path 1: check last 64KB (where standard protobuf / appended metadata lives)
            val tailLen = minOf(fileLength, WINDOW_BYTES.toLong()).toInt()
            val tailBytes = ByteArray(tailLen)
            raf.seek(fileLength - tailLen)
            raf.readFully(tailBytes)
            if (containsSampleRateMetadataEntry(tailBytes, tailLen)) {
                return true
            }

            // Fast path 2: check first 64KB (where head-embedded metadata lives)
            val headLen = minOf(fileLength, WINDOW_BYTES.toLong()).toInt()
            val headBytes = ByteArray(headLen)
            raf.seek(0)
            raf.readFully(headBytes)
            if (containsSampleRateMetadataEntry(headBytes, headLen)) {
                return true
            }
        }

        // Slow path: only if file > 64KB and metadata wasn't in head or tail, stream overlapping windows
        if (fileLength <= WINDOW_BYTES.toLong()) {
            return false
        }

        val buffer = ByteArray(WINDOW_BYTES + OVERLAP_BYTES)
        var carry = 0
        FileInputStream(onnxFile).use { fis ->
            while (true) {
                val read = fis.read(buffer, carry, WINDOW_BYTES)
                if (read <= 0) break
                val total = carry + read
                if (containsSampleRateMetadataEntry(buffer, total)) {
                    return true
                }
                carry = minOf(OVERLAP_BYTES, total)
                System.arraycopy(buffer, total - carry, buffer, 0, carry)
            }
        }
        return false
    }

    /**
     * Checks if the given ONNX file contains a serialized Protobuf `ModelProto.metadata_props`
     * entry for `sample_rate`. If missing, appends the required Protobuf metadata fields
     * to the file in-place.
     */
    fun ensureMetadata(
        onnxFile: File,
        sampleRate: Int = 22050,
        voice: String = "vi",
        nSpeakers: Int = 1,
        language: String = if (voice.startsWith("vi", ignoreCase = true)) "Vietnamese" else voice,
    ): Boolean {
        if (!onnxFile.exists() || onnxFile.length() < 1024) return false

        return runCatching {
            if (hasSampleRateMetadata(onnxFile)) {
                return true
            }

            val safeVoice = voice.ifBlank { "vi" }
            val safeSpeakers = nSpeakers.coerceAtLeast(1)
            val resolvedLanguage = language.ifBlank {
                if (safeVoice.startsWith("vi", ignoreCase = true)) "Vietnamese" else safeVoice
            }

            // Append required metadata for sherpa-onnx VITS Piper loader
            val meta = linkedMapOf(
                "model_type" to "vits",
                "comment" to "piper",
                "language" to resolvedLanguage,
                "voice" to safeVoice,
                "has_espeak" to "1",
                "n_speakers" to safeSpeakers.toString(),
                "sample_rate" to sampleRate.toString(),
            )

            val out = ByteArrayOutputStream()
            for ((k, v) in meta) {
                out.write(makeStringEntry(k, v))
            }

            FileOutputStream(onnxFile, true).use { fos ->
                fos.write(out.toByteArray())
                fos.flush()
            }
            runCatching {
                Log.i(TAG, "Injected Piper Protobuf metadata into ${onnxFile.name} (sampleRate=$sampleRate, voice=$safeVoice, nSpeakers=$safeSpeakers)")
            }
            true
        }.onFailure { err ->
            runCatching {
                Log.e(TAG, "Failed to inject metadata into ${onnxFile.absolutePath}", err)
            }
        }.getOrDefault(false)
    }
}
