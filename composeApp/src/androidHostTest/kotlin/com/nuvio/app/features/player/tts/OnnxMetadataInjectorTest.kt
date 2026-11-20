package com.nuvio.app.features.player.tts

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnnxMetadataInjectorTest {

    private fun buildProtobufStringEntry(key: String, value: String, reverseFieldOrder: Boolean = false): ByteArray {
        val kBytes = key.toByteArray(Charsets.UTF_8)
        val vBytes = value.toByteArray(Charsets.UTF_8)
        val entry = ByteArrayOutputStream()
        if (!reverseFieldOrder) {
            entry.write(0x0a)
            entry.write(kBytes.size)
            entry.write(kBytes)
            entry.write(0x12)
            entry.write(vBytes.size)
            entry.write(vBytes)
        } else {
            entry.write(0x12)
            entry.write(vBytes.size)
            entry.write(vBytes)
            entry.write(0x0a)
            entry.write(kBytes.size)
            entry.write(kBytes)
        }
        val entryBytes = entry.toByteArray()
        val wrapper = ByteArrayOutputStream()
        wrapper.write(0x72)
        wrapper.write(entryBytes.size)
        wrapper.write(entryBytes)
        return wrapper.toByteArray()
    }

    @Test
    fun testEnsureMetadataInjectsSampleRateAndPiperKeys() {
        val tempFile = File.createTempFile("test_model", ".onnx")
        tempFile.deleteOnExit()

        // Write dummy ONNX protobuf payload (1.5KB of zeros)
        val dummyData = ByteArray(1536) { 0 }
        tempFile.writeBytes(dummyData)

        val firstRunResult = OnnxMetadataInjector.ensureMetadata(
            onnxFile = tempFile,
            sampleRate = 22050,
            voice = "vi",
            nSpeakers = 1,
        )
        assertTrue(firstRunResult, "First injection should succeed")

        val fileBytes = tempFile.readBytes()
        val fileContentString = String(fileBytes, Charsets.ISO_8859_1)

        assertTrue(fileContentString.contains("sample_rate"), "Must contain sample_rate")
        assertTrue(fileContentString.contains("22050"), "Must contain 22050")
        assertTrue(fileContentString.contains("model_type"), "Must contain model_type")
        assertTrue(fileContentString.contains("piper"), "Must contain piper comment")
        assertTrue(fileContentString.contains("has_espeak"), "Must contain has_espeak")
        assertTrue(fileContentString.contains("n_speakers"), "Must contain n_speakers")
        assertTrue(fileContentString.contains("Vietnamese"), "Must contain Vietnamese language")
        assertTrue(OnnxMetadataInjector.containsSampleRateMetadataEntry(fileBytes))

        val initialLength = tempFile.length()

        // Calling ensureMetadata again should detect existing sample_rate and not append twice
        val secondRunResult = OnnxMetadataInjector.ensureMetadata(
            onnxFile = tempFile,
            sampleRate = 22050,
            voice = "vi",
        )
        assertTrue(secondRunResult, "Second run should succeed idempotently")
        assertEquals(initialLength, tempFile.length(), "File size should not change on second call")
    }

    @Test
    fun testFalsePositiveNodeOrTensorNamesDoNotSkipMetadataInjection() {
        val tempFile = File.createTempFile("test_false_positive", ".onnx")
        tempFile.deleteOnExit()

        // Simulate raw Piper ONNX containing "sample_rate" in graph node/tensor names or non-metadata protobuf fields
        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(1200) { 0x01 })
        payload.write("/enc/sample_rate_projection/Conv_output_0".toByteArray(Charsets.UTF_8))
        // StringStringEntryProto-like bytes WITHOUT ModelProto.metadata_props tag 0x72
        payload.write("\n\u000bsample_rate\u0012\u000522050".toByteArray(Charsets.ISO_8859_1))
        // 0x72 entry with non-numeric value must also not count as valid sample_rate metadata
        payload.write(buildProtobufStringEntry("sample_rate", "float"))
        payload.write(ByteArray(300) { 0x02 })
        tempFile.writeBytes(payload.toByteArray())

        val sizeBefore = tempFile.length()
        assertFalse(OnnxMetadataInjector.containsSampleRateMetadataEntry(tempFile.readBytes()))

        val injected = OnnxMetadataInjector.ensureMetadata(
            onnxFile = tempFile,
            sampleRate = 22050,
            voice = "vi",
        )
        assertTrue(injected, "Injection must proceed despite tensor/node names containing 'sample_rate'")
        assertTrue(tempFile.length() > sizeBefore, "Metadata must be appended")

        // Second pass must now be idempotent
        val sizeAfter = tempFile.length()
        assertTrue(OnnxMetadataInjector.ensureMetadata(tempFile, 22050, "vi"))
        assertEquals(sizeAfter, tempFile.length(), "Second call must not duplicate injected metadata")
    }

    @Test
    fun testExistingProtobufMetadataIsDetectedWithoutModification() {
        val tempFile = File.createTempFile("test_existing_meta", ".onnx")
        tempFile.deleteOnExit()

        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(2048) { 0x05 })
        payload.write(buildProtobufStringEntry("model_type", "vits"))
        payload.write(buildProtobufStringEntry("comment", "piper"))
        payload.write(buildProtobufStringEntry("sample_rate", "22050"))
        tempFile.writeBytes(payload.toByteArray())

        val initialLength = tempFile.length()
        assertTrue(OnnxMetadataInjector.ensureMetadata(tempFile, sampleRate = 22050, voice = "vi"))
        assertEquals(initialLength, tempFile.length(), "Existing VAIS1000-style metadata must not be duplicated")
    }

    @Test
    fun testReverseFieldOrderAndMiddleChunkBoundaryMetadataDetection() {
        val tempFile = File.createTempFile("test_middle_meta", ".onnx")
        tempFile.deleteOnExit()

        // Place valid reverse-order metadata straddling the 64KB chunk boundary (outside head & tail 64KB windows)
        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(65530) { 0x03 })
        payload.write(buildProtobufStringEntry("sample_rate", "16000", reverseFieldOrder = true))
        payload.write(ByteArray(70000) { 0x04 })
        tempFile.writeBytes(payload.toByteArray())

        val initialLength = tempFile.length()
        assertTrue(OnnxMetadataInjector.ensureMetadata(tempFile, sampleRate = 22050, voice = "vi"))
        assertEquals(initialLength, tempFile.length(), "Metadata straddling 64KB middle chunk must be detected")
    }
}
