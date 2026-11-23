package com.nuvio.app.features.player.tts

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TtsPhoneticDictionaryAssetTest {

    private fun locateBundledDictFile(): File {
        val candidates = listOf(
            File("src/commonMain/composeResources/files/tts_en_vi_dict.tsv"),
            File("composeApp/src/commonMain/composeResources/files/tts_en_vi_dict.tsv"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not locate tts_en_vi_dict.tsv in ${candidates.map { it.absolutePath }}")
    }

    @Test
    fun bundledTsvDictionaryIsValidCompleteAndProperlyIndexed() {
        val dictFile = locateBundledDictFile()
        val rawTsv = dictFile.readText(Charsets.UTF_8)

        var badLines = 0
        val seenKeys = HashSet<String>()
        var duplicateKeys = 0
        for (line in rawTsv.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val parts = trimmed.split('\t')
            if (parts.size < 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                badLines++
                continue
            }
            val normalizedKey = parts[0].trim().lowercase().replace('\u2019', '\'')
            if (!seenKeys.add(normalizedKey)) {
                duplicateKeys++
            }
        }

        assertEquals(0, badLines, "Found malformed lines in tts_en_vi_dict.tsv")
        assertEquals(0, duplicateKeys, "Found duplicate keys in tts_en_vi_dict.tsv")

        val parsed = TtsPhoneticDictionary.parseFromText(rawTsv)
        val totalEntries = parsed.phrases.size + parsed.words.size
        assertTrue(totalEntries >= 1000, "Expected >= 1000 dictionary entries, got $totalEntries")

        // Hyphenated and punctuated entries must be indexed in phrasesByFirstWord, not orphaned in words.
        val obiBucket = parsed.phrasesByFirstWord["obi"]
        assertNotNull(obiBucket)
        val kenobiIdx = obiBucket.indexOfFirst { it.rawKey == "obi-wan kenobi" }
        val obiWanIdx = obiBucket.indexOfFirst { it.rawKey == "obi-wan" }
        assertTrue(kenobiIdx >= 0 && obiWanIdx >= 0 && kenobiIdx < obiWanIdx)

        assertTrue(parsed.phrasesByFirstWord["spider"]?.any { it.rawKey == "spider-man" } == true)
        assertTrue(parsed.phrasesByFirstWord["wi"]?.any { it.rawKey == "wi-fi" } == true)
        assertTrue(parsed.phrasesByFirstWord["middle"]?.any { it.rawKey == "middle-earth" } == true)
        assertTrue(parsed.phrasesByFirstWord["feyd"]?.any { it.rawKey == "feyd-rautha" } == true)
        assertTrue(parsed.phrasesByFirstWord["k"]?.any { it.rawKey == "k.o" } == true)
    }

    @Test
    fun realDictionaryTransliteratesRepresentativeNamesFastWithoutRegexOverhead() {
        val parsed = TtsPhoneticDictionary.parseFromText(locateBundledDictFile().readText(Charsets.UTF_8))
        val transliterator = EnglishToVietnameseTransliterator(parsed)

        val input = "Obi-Wan Kenobi xem Fast & Furious cùng Tony Stark và Spider-Man ở New York qua Wi-Fi với FBI!"
        val startNs = System.nanoTime()
        var output = ""
        repeat(500) {
            output = transliterator.transliterate(input)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        assertTrue(output.contains("Ô bi Oan Kê nô bi"), "Unexpected output: $output")
        assertTrue(output.contains("Phát en Phiu ri ớt"), "Unexpected output: $output")
        assertTrue(output.contains("Tô ni Xtác"), "Unexpected output: $output")
        assertTrue(output.contains("Xpai đơ Men"), "Unexpected output: $output")
        assertTrue(output.contains("Niu Yoóc"), "Unexpected output: $output")
        assertTrue(output.contains("oai phai"), "Unexpected output: $output")
        assertTrue(output.contains("ép bi ai") || output.contains("Ép bi ai"), "Unexpected output: $output")
        assertTrue(elapsedMs < 500L, "500 transliterations took ${elapsedMs}ms (expected < 500ms)")
    }
}
