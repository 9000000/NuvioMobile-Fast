package com.nuvio.app.features.player.tts

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res

/**
 * Loads, parses, and caches the English-to-Vietnamese phonetic dictionary bundled in
 * Compose Multiplatform resources (`files/tts_en_vi_dict.tsv`).
 */
object TtsPhoneticDictionary {
    const val DICT_RESOURCE_PATH = "files/tts_en_vi_dict.tsv"
    private const val TAG = "TtsPhoneticDictionary"
    private const val INITIAL_BUCKET_CAPACITY = 2
    private const val RIGHT_SINGLE_QUOTE = '\u2019'

    data class PhraseEntry(
        val tokens: List<String>,
        val rawKey: String,
        val phonetic: String,
    )

    data class ParsedDictionary(
        val phrases: List<PhraseEntry>,
        val words: Map<String, String>,
        val phrasesByFirstWord: Map<String, List<PhraseEntry>> = emptyMap(),
    ) {
        companion object {
            val EMPTY = ParsedDictionary(
                phrases = emptyList(),
                words = emptyMap(),
                phrasesByFirstWord = emptyMap(),
            )
        }
    }

    private val cachedDictionary = atomic<ParsedDictionary?>(null)
    private val preloadStarted = atomic(false)

    /**
     * Parses a UTF-8 TSV phonetic dictionary (`english_term<TAB>vietnamese_phonetic`).
     *
     * Multi-word phrases and compound keys containing separators (such as hyphens, dots, or
     * ampersands like `spider-man`, `wi-fi`, `k.o`, `fast & furious`) are indexed by their
     * first word token so runtime lookup stays O(1) without per-cue regex compilation.
     */
    fun parseFromText(rawTsv: String): ParsedDictionary {
        if (rawTsv.isBlank()) return ParsedDictionary.EMPTY

        val phrases = mutableListOf<PhraseEntry>()
        val words = HashMap<String, String>()

        for (line in rawTsv.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val tabIndex = trimmed.indexOf('\t')
            if (tabIndex <= 0) continue

            val key = trimmed.substring(0, tabIndex)
                .trim()
                .lowercase()
                .replace(RIGHT_SINGLE_QUOTE, '\'')
            val rest = trimmed.substring(tabIndex + 1)
            val secondTab = rest.indexOf('\t')
            val value = (if (secondTab >= 0) rest.substring(0, secondTab) else rest).trim()
            if (key.isEmpty() || value.isEmpty()) continue

            val tokens = extractWordTokens(key)
            if (isPhraseKey(key, tokens)) {
                phrases.add(PhraseEntry(tokens = tokens, rawKey = key, phonetic = value))
            } else {
                words[key] = value
            }
        }

        // Longest token count and string length first so "obi-wan kenobi" wins over "obi-wan".
        phrases.sortWith(
            compareByDescending<PhraseEntry> { it.tokens.size }
                .thenByDescending { it.rawKey.length },
        )

        val byFirstWord = HashMap<String, MutableList<PhraseEntry>>()
        for (phrase in phrases) {
            val firstWord = phrase.tokens.firstOrNull() ?: continue
            byFirstWord.getOrPut(firstWord) { ArrayList(INITIAL_BUCKET_CAPACITY) }.add(phrase)
        }

        return ParsedDictionary(
            phrases = phrases,
            words = words,
            phrasesByFirstWord = byFirstWord,
        )
    }

    /**
     * Asynchronously loads the bundled TSV dictionary off the main thread so subtitle cues
     * incur zero file I/O latency.
     */
    fun preloadAsync(scope: CoroutineScope) {
        if (cachedDictionary.value != null) return
        if (!preloadStarted.compareAndSet(expect = false, update = true)) return

        scope.launch(Dispatchers.Default) {
            val loaded = runCatching {
                val rawTsv = Res.readBytes(DICT_RESOURCE_PATH).decodeToString()
                parseFromText(rawTsv)
            }.onFailure { error ->
                Logger.w(TAG, error) { "Failed loading TTS phonetic dictionary from $DICT_RESOURCE_PATH" }
            }.getOrNull()

            if (loaded != null) {
                cachedDictionary.value = loaded
            } else {
                preloadStarted.value = false
            }
        }
    }

    /**
     * Returns the cached dictionary immediately without blocking. Falls back to
     * [ParsedDictionary.EMPTY] while initial background loading is still in progress.
     */
    fun getDictionary(): ParsedDictionary = cachedDictionary.value ?: ParsedDictionary.EMPTY

    private fun isPhraseKey(normalizedKey: String, tokens: List<String>): Boolean {
        if (tokens.size > 1) return true
        return normalizedKey.any { !isDictionaryWordChar(it) }
    }

    private fun extractWordTokens(normalizedKey: String): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < normalizedKey.length) {
            while (index < normalizedKey.length && !isDictionaryWordChar(normalizedKey[index])) {
                index++
            }
            val start = index
            while (index < normalizedKey.length && isDictionaryWordChar(normalizedKey[index])) {
                index++
            }
            if (index > start) {
                tokens.add(normalizedKey.substring(start, index))
            }
        }
        return tokens
    }

    private fun isDictionaryWordChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '\''
}
