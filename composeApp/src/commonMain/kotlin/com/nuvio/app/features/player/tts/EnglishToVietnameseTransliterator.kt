package com.nuvio.app.features.player.tts

/**
 * Transliterates English proper names, acronyms, and common terms into Vietnamese phonetic text
 * so Vietnamese TTS engines (both Google/Apple system voices and Piper Vietnamese ONNX models)
 * pronounce them naturally with zero voice switching and zero per-cue regex overhead.
 */
class EnglishToVietnameseTransliterator(
    private val dictionaryProvider: () -> TtsPhoneticDictionary.ParsedDictionary,
) {
    constructor(preset: TtsPhoneticDictionary.ParsedDictionary) : this(
        dictionaryProvider = { preset },
    )

    companion object {
        private const val DEFAULT_BUFFER_PADDING = 32
        private const val MIN_POSSESSIVE_TOKEN_LENGTH = 3
        private const val MIN_ACRONYM_LENGTH = 2
        private const val MAX_ACRONYM_LENGTH = 5
        private const val MIN_PROPER_NAME_LENGTH = 3
        private const val MIN_CAPITALIZED_NAME_LENGTH = 4
        private const val MIN_FINAL_S_LENGTH = 3
        private const val RIGHT_SINGLE_QUOTE = '\u2019'
        private const val POSSESSIVE_SUFFIX_VI = " của"

        val Default: EnglishToVietnameseTransliterator = EnglishToVietnameseTransliterator(
            dictionaryProvider = { TtsPhoneticDictionary.getDictionary() },
        )

        /**
         * Common non-diacritic Vietnamese words that must never be transliterated even when
         * capitalized at the start of a subtitle sentence.
         */
        val VIETNAMESE_STOP_WORDS: Set<String> = hashSetOf(
            "anh", "em", "con", "cho", "khi", "nhung", "trong", "nha", "minh", "nam",
            "lan", "mai", "hai", "hoa", "huong", "linh", "trang", "tuan", "hung", "dung",
            "thanh", "quang", "phuong", "thuy", "ngan", "duc", "giang", "van", "ba", "me",
            "chu", "bac", "co", "di", "chau", "ong", "toi", "ta", "ban", "no", "ho", "chung",
            "nguoi", "cai", "chiec", "mot", "bon", "sau", "bay", "tam", "chin", "muoi",
            "tram", "trieu", "ty", "khong", "duoc", "bi", "phai", "se", "da", "dang",
            "vua", "moi", "sap", "rat", "qua", "lam", "het", "roi", "chua", "lai", "cu",
            "hay", "hoac", "neu", "thi", "vi", "nen", "de", "do", "boi", "voi", "va",
            "cung", "nhu", "ma", "ai", "gi", "nao", "dau", "sao", "the", "nay", "kia",
            "day", "ay", "tren", "duoi", "ngoai", "truoc", "giua", "ben", "canh", "gan",
            "xa", "ve", "tu", "den", "vao", "ra", "len", "xuong", "thay", "xem", "nghe",
            "noi", "biet", "hieu", "muon", "can", "thich", "yeu", "ghet", "an", "uong",
            "ngu", "ngoi", "chay", "tao", "lay", "dua", "nhan", "giu", "bo", "quen",
            "nho", "tin", "so", "lo", "vui", "buon", "giup", "cuu", "chet", "song", "mau",
            "nhanh", "cham", "tot", "xau", "sai", "that", "gia", "cao", "thap", "to", "be",
            "lon", "nhieu", "it", "nang", "nhe", "nong", "lanh", "am", "mat", "sang",
            "dep", "kho", "nua", "luon", "ngay", "gio", "phut", "giay", "thang", "tien",
            "cua", "xe", "duong", "pho", "lang", "dong", "tay", "troi", "dat", "nuoc",
            "lua", "may", "mua", "bien", "nui", "rung", "cay", "la", "chim", "ca",
        )

        /** Characters that unambiguously identify Vietnamese accented words. */
        private val VIETNAMESE_SPECIFIC_CHARS: Set<Char> = hashSetOf(
            'à', 'á', 'ả', 'ã', 'ạ', 'ă', 'ằ', 'ắ', 'ẳ', 'ẵ', 'ặ', 'â', 'ầ', 'ấ', 'ẩ', 'ẫ', 'ậ',
            'è', 'é', 'ẻ', 'ẽ', 'ẹ', 'ê', 'ề', 'ế', 'ể', 'ễ', 'ệ',
            'ì', 'í', 'ỉ', 'ĩ', 'ị',
            'ò', 'ó', 'ỏ', 'õ', 'ọ', 'ô', 'ồ', 'ố', 'ổ', 'ỗ', 'ộ', 'ơ', 'ờ', 'ớ', 'ở', 'ỡ', 'ợ',
            'ù', 'ú', 'ủ', 'ũ', 'ụ', 'ư', 'ừ', 'ứ', 'ử', 'ữ', 'ự',
            'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ', 'đ',
            'À', 'Á', 'Ả', 'Ã', 'Ạ', 'Ă', 'Ằ', 'Ắ', 'Ẳ', 'Ẵ', 'Ặ', 'Â', 'Ầ', 'Ấ', 'Ẩ', 'Ẫ', 'Ậ',
            'È', 'É', 'Ẻ', 'Ẽ', 'Ẹ', 'Ê', 'Ề', 'Ế', 'Ể', 'Ễ', 'Ệ',
            'Ì', 'Í', 'Ỉ', 'Ĩ', 'Ị',
            'Ò', 'Ó', 'Ỏ', 'Õ', 'Ọ', 'Ô', 'Ồ', 'Ố', 'Ổ', 'Ỗ', 'Ộ', 'Ơ', 'Ờ', 'Ớ', 'Ở', 'Ỡ', 'Ợ',
            'Ù', 'Ú', 'Ủ', 'Ũ', 'Ụ', 'Ư', 'Ừ', 'Ứ', 'Ử', 'Ữ', 'Ự',
            'Ỳ', 'Ý', 'Ỷ', 'Ỹ', 'Ỵ', 'Đ',
        )

        private val ACRONYM_SPELLOUT: Map<Char, String> = mapOf(
            'A' to "ây", 'B' to "bi", 'C' to "xi", 'D' to "đi", 'E' to "i",
            'F' to "ép", 'G' to "gi", 'H' to "hắt", 'I' to "ai", 'J' to "giây",
            'K' to "cây", 'L' to "en", 'M' to "em", 'N' to "en", 'O' to "ô",
            'P' to "pi", 'Q' to "kiu", 'R' to "a", 'S' to "ét", 'T' to "ti",
            'U' to "diu", 'V' to "vi", 'W' to "đáp liu", 'X' to "ếch", 'Y' to "oai",
            'Z' to "dét",
        )

        private val ENGLISH_CLUSTERS: List<String> = listOf(
            "sh", "ck", "wh", "tion", "sion",
            "str", "br", "cr", "dr", "gr", "pr", "ight",
        )

        private val TRAILING_URL_PUNCTUATION: Set<Char> = hashSetOf(
            '.', ',', '!', '?', ';', ':', ')', ']', '}', '"', '\'', RIGHT_SINGLE_QUOTE,
        )

        private fun isWordChar(ch: Char): Boolean =
            ch.isLetterOrDigit() || ch == '\'' || ch == RIGHT_SINGLE_QUOTE

        private fun isWordBoundaryChar(ch: Char): Boolean =
            ch == '_' || ch.isLetterOrDigit()
    }

    /**
     * Transliterates English phrases, names, acronyms, and loanwords inside [text] into
     * Vietnamese phonetic equivalents while preserving Vietnamese words, punctuation, URLs,
     * and email addresses.
     */
    fun transliterate(text: String): String {
        if (text.isBlank()) return text

        val dict = dictionaryProvider()
        val phrasesByFirstWord = dict.phrasesByFirstWord
        val words = dict.words
        val normalizedLower = text.lowercase().replace(RIGHT_SINGLE_QUOTE, '\'')
        val sb = StringBuilder(text.length + DEFAULT_BUFFER_PADDING)

        var index = 0
        while (index < text.length) {
            if (!isWordChar(text[index])) {
                sb.append(text[index])
                index++
                continue
            }

            val protectedEnd = scanProtectedSpanEnd(text, normalizedLower, index)
            if (protectedEnd > index) {
                sb.append(text, index, protectedEnd)
                index = protectedEnd
                continue
            }

            var tokenEnd = index + 1
            while (tokenEnd < text.length && isWordChar(text[tokenEnd])) {
                tokenEnd++
            }

            val lowerToken = normalizedLower.substring(index, tokenEnd)
            val matchedPhrase = if (phrasesByFirstWord.isNotEmpty() &&
                (index == 0 || !isWordBoundaryChar(text[index - 1]))
            ) {
                matchPhraseAt(text, normalizedLower, index, lowerToken, phrasesByFirstWord)
            } else {
                null
            }

            if (matchedPhrase != null) {
                sb.append(matchedPhrase.phonetic)
                index += matchedPhrase.rawKey.length
                continue
            }

            val token = text.substring(index, tokenEnd)
            sb.append(transliterateSingleToken(token, lowerToken, words))
            index = tokenEnd
        }

        return sb.toString()
    }

    private fun matchPhraseAt(
        text: String,
        normalizedLower: String,
        startIndex: Int,
        firstWordLower: String,
        phrasesByFirstWord: Map<String, List<TtsPhoneticDictionary.PhraseEntry>>,
    ): TtsPhoneticDictionary.PhraseEntry? {
        val candidates = phrasesByFirstWord[firstWordLower] ?: return null
        for (candidate in candidates) {
            val endIndex = startIndex + candidate.rawKey.length
            if (endIndex <= normalizedLower.length &&
                normalizedLower.startsWith(candidate.rawKey, startIndex) &&
                (endIndex == text.length || !isWordBoundaryChar(text[endIndex]))
            ) {
                return candidate
            }
        }
        return null
    }

    /**
     * Detects inline URLs (`http://`, `https://`, `www.`) and email addresses starting at
     * [startIndex], returning the exclusive end index to copy untouched.
     */
    private fun scanProtectedSpanEnd(
        text: String,
        normalizedLower: String,
        startIndex: Int,
    ): Int {
        val isUrlStart = normalizedLower.startsWith("http://", startIndex) ||
            normalizedLower.startsWith("https://", startIndex) ||
            normalizedLower.startsWith("www.", startIndex)

        if (isUrlStart) {
            var end = startIndex
            while (end < text.length && !text[end].isWhitespace()) {
                end++
            }
            while (end > startIndex && text[end - 1] in TRAILING_URL_PUNCTUATION) {
                end--
            }
            return end
        }

        var cursor = startIndex
        var atIndex = -1
        var dotAfterAt = -1
        while (cursor < text.length && !text[cursor].isWhitespace()) {
            val ch = text[cursor]
            if (ch == '@' && atIndex < 0) {
                atIndex = cursor
            } else if (atIndex > startIndex && ch == '.' && cursor > atIndex + 1) {
                dotAfterAt = cursor
            }
            cursor++
        }

        if (atIndex > startIndex && dotAfterAt > atIndex + 1) {
            var end = cursor
            while (end > startIndex && text[end - 1] in TRAILING_URL_PUNCTUATION) {
                end--
            }
            if (end > dotAfterAt + 1) {
                return end
            }
        }
        return -1
    }

    private fun transliterateSingleToken(
        token: String,
        normalizedLowerToken: String,
        dictionaryWords: Map<String, String>,
    ): String {
        if (token.any { it in VIETNAMESE_SPECIFIC_CHARS }) {
            return token
        }

        var baseToken = token
        var lowerBase = normalizedLowerToken
        var possessiveSuffix = ""
        if (token.length >= MIN_POSSESSIVE_TOKEN_LENGTH && normalizedLowerToken.endsWith("'s")) {
            baseToken = token.substring(0, token.length - 2)
            lowerBase = normalizedLowerToken.substring(0, normalizedLowerToken.length - 2)
            possessiveSuffix = POSSESSIVE_SUFFIX_VI
        }

        if (lowerBase in VIETNAMESE_STOP_WORDS) {
            return token
        }

        val dictMatch = dictionaryWords[lowerBase]
        if (dictMatch != null) {
            return if (possessiveSuffix.isNotEmpty()) "$dictMatch$possessiveSuffix" else dictMatch
        }

        if (baseToken.length in MIN_ACRONYM_LENGTH..MAX_ACRONYM_LENGTH &&
            baseToken.all { it in 'A'..'Z' }
        ) {
            val spelled = baseToken.map { ACRONYM_SPELLOUT[it] ?: it.toString() }.joinToString(" ")
            return if (possessiveSuffix.isNotEmpty()) "$spelled$possessiveSuffix" else spelled
        }

        if (isLikelyEnglishProperName(baseToken, lowerBase)) {
            val transliterated = applyRuleBasedPhonetics(baseToken, lowerBase)
            if (transliterated != null) {
                return if (possessiveSuffix.isNotEmpty()) "$transliterated$possessiveSuffix" else transliterated
            }
        }

        return token
    }

    private fun isLikelyEnglishProperName(token: String, lower: String): Boolean {
        if (token.length < MIN_PROPER_NAME_LENGTH) return false
        if (lower in VIETNAMESE_STOP_WORDS) return false

        val hasEnglishExclusiveLetters = lower.any { it == 'j' || it == 'w' || it == 'z' || it == 'f' }
        val hasEnglishClusters = ENGLISH_CLUSTERS.any { lower.contains(it) }
        val startsWithCapital = token[0].isUpperCase() && token.substring(1).any { it.isLowerCase() }

        return hasEnglishExclusiveLetters ||
            hasEnglishClusters ||
            (startsWithCapital && lower.length >= MIN_CAPITALIZED_NAME_LENGTH)
    }

    private fun applyRuleBasedPhonetics(originalToken: String, lowerName: String): String? {
        var result = lowerName
            .replace("tion", "sần")
            .replace("sion", "sần")
            .replace("ight", "ait")
            .replace("ough", "ốp")
            .replace("ee", "i")
            .replace("ea", "i")
            .replace("oo", "u")
            .replace("ck", "c")
            .replace("sh", "s")
            .replace("wh", "o")
            .replace("w", "u")
            .replace("j", "gi")
            .replace("z", "d")
            .replace("f", "ph")

        if (result.endsWith("s") && result.length >= MIN_FINAL_S_LENGTH && !result.endsWith("ss")) {
            result = result.dropLast(1) + "t"
        }

        // If no phonetic transformation actually occurred, keep the original token untouched.
        if (result.isBlank() || result == lowerName) {
            return null
        }

        return if (originalToken[0].isUpperCase()) {
            result.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        } else {
            result
        }
    }
}
