package com.nuvio.app.features.player.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiperConfigParserTest {

    private val sampleJson = """
    {
      "audio": {
        "sample_rate": 22050
      },
      "espeak": {
        "voice": "vi"
      },
      "phoneme_type": "espeak",
      "num_symbols": 256,
      "num_speakers": 1,
      "inference": {
        "noise_scale": 0.667,
        "length_scale": 1.0,
        "noise_w": 0.8
      },
      "phoneme_id_map": {
        "b": [15],
        "_": [0],
        "^": [1],
        "$": [2],
        " ": [3],
        "a": [14]
      }
    }
    """.trimIndent()

    @Test
    fun testParseConfigExtractsMetadataAndTokens() {
        val config = PiperConfigParser.parseConfig(sampleJson)

        assertEquals(22050, config.sampleRate)
        assertEquals("vi", config.espeakVoice)
        assertEquals(1, config.numSpeakers)
        assertEquals(0.667f, config.noiseScale)
        assertEquals(1.0f, config.lengthScale)
        assertEquals(0.8f, config.noiseW)

        val expectedTokens = listOf(
            "_ 0",
            "^ 1",
            "$ 2",
            "  3",
            "a 14",
            "b 15",
        ).joinToString("\n") + "\n"
        assertEquals(expectedTokens, config.tokensText)
        assertFalse(config.tokensText.dropLast(1).contains("\n\n"), "tokens.txt must not contain blank lines")
    }

    @Test
    fun testNormalizeHuggingFaceUrlConvertsBlobToResolve() {
        val rawBlobUrl = "https://huggingface.co/hoanglinhn0/Model/blob/main/ngochuyen5.onnx"
        val normalized = PiperConfigParser.normalizeHuggingFaceUrl(rawBlobUrl)
        assertEquals("https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx", normalized)

        val alreadyResolve = "https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx"
        assertEquals(alreadyResolve, PiperConfigParser.normalizeHuggingFaceUrl(alreadyResolve))
    }

    @Test
    fun testInferJsonUrlAppendsJsonToOnnx() {
        val onnxUrl = "https://huggingface.co/hoanglinhn0/Model/blob/main/ngochuyen5.onnx"
        val jsonUrl = PiperConfigParser.inferJsonUrl(onnxUrl)
        assertEquals("https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx.json", jsonUrl)

        val onnxWithQuery = "https://huggingface.co/hoanglinhn0/Model/blob/main/ngochuyen5.onnx?download=true"
        assertEquals(
            "https://huggingface.co/hoanglinhn0/Model/resolve/main/ngochuyen5.onnx.json?download=true",
            PiperConfigParser.inferJsonUrl(onnxWithQuery),
        )
    }
}
