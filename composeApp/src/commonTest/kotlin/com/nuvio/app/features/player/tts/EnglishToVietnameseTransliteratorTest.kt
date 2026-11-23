package com.nuvio.app.features.player.tts

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnglishToVietnameseTransliteratorTest {

    private lateinit var transliterator: EnglishToVietnameseTransliterator

    @BeforeTest
    fun setUp() {
        val sampleTsv = """
            # Sample phonetic dictionary for KMP commonTest
            tony stark	Tô ni Xtác
            doctor strange	Đốc tơ Xtơ ren
            john wick	Giôn Uých
            harry potter	Ha ri Pót tơ
            new york	Niu Yoóc
            los angeles	Lót An giơ lét
            obi-wan kenobi	Ô bi Oan Kê nô bi
            obi-wan	Ô bi Oan
            spider-man	Xpai đơ Men
            fast & furious	Phát en Phiu ri ớt
            middle-earth	Trung Địa
            wi-fi	oai phai
            k.o	cây ô
            king's landing	Kinh đô King Xlen đing
            you're welcome	du a oen căm
            john	Giôn
            michael	Mai cồ
            thanos	Tha nốt
            sherlock	Sơ lốc
            christopher	Crít xtô phơ
            hello	hê lô
            sorry	so ri
            thank you	thanh kiu
            fbi	ép bi ai
            cia	xi ai ây
            ok	ô kê
            boss	bót
        """.trimIndent()
        val parsed = TtsPhoneticDictionary.parseFromText(sampleTsv)
        transliterator = EnglishToVietnameseTransliterator(parsed)
    }

    @Test
    fun transliteratesMultiWordCharacterAndPlaceNames() {
        val output = transliterator.transliterate("Tôi đã gặp Tony Stark ở New York.")
        assertTrue(output.contains("Tô ni Xtác"))
        assertTrue(output.contains("Niu Yoóc"))
        assertFalse(output.contains("Tony Stark"))
        assertFalse(output.contains("New York"))
    }

    @Test
    fun transliteratesHyphenatedAndPunctuatedCompoundTermsWithLongestPrefixPriority() {
        val input = "Obi-Wan Kenobi và Obi-Wan gặp Spider-Man qua Wi-Fi ở Middle-Earth, xem Fast & Furious rồi K.O!"
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("Ô bi Oan Kê nô bi"), "Unexpected output: $output")
        assertTrue(output.contains("và Ô bi Oan gặp"), "Unexpected output: $output")
        assertTrue(output.contains("Xpai đơ Men"), "Unexpected output: $output")
        assertTrue(output.contains("oai phai"), "Unexpected output: $output")
        assertTrue(output.contains("Trung Địa"), "Unexpected output: $output")
        assertTrue(output.contains("Phát en Phiu ri ớt"), "Unexpected output: $output")
        assertTrue(output.contains("cây ô!"), "Unexpected output: $output")
    }

    @Test
    fun transliteratesSingleWordNamesAndCommonWords() {
        val output = transliterator.transliterate("Hello, Thanos đã gặp Michael. Thank you, boss!")
        assertTrue(output.contains("hê lô"))
        assertTrue(output.contains("Tha nốt"))
        assertTrue(output.contains("Mai cồ"))
        assertTrue(output.contains("thanh kiu"))
        assertTrue(output.contains("bót!"))
    }

    @Test
    fun spellsOutDictionaryAndUnknownAcronyms() {
        val output = transliterator.transliterate("Đặc vụ FBI, CIA và MIT đang điều tra KGB.")
        assertTrue(output.contains("ép bi ai"))
        assertTrue(output.contains("xi ai ây"))
        assertTrue(output.contains("em ai ti"))
        assertTrue(output.contains("cây gi bi"))
    }

    @Test
    fun handlesAsciiAndCurlyApostrophePossessivesAndPhrases() {
        val asciiOutput = transliterator.transliterate("Đây là John's xe hơi tại King's Landing.")
        assertTrue(asciiOutput.contains("Giôn của"), "Unexpected output: $asciiOutput")
        assertTrue(asciiOutput.contains("Kinh đô King Xlen đing"), "Unexpected output: $asciiOutput")

        val curlyOutput = transliterator.transliterate("Đây là John\u2019s xe hơi tại King\u2019s Landing, you\u2019re welcome!")
        assertTrue(curlyOutput.contains("Giôn của"), "Unexpected output: $curlyOutput")
        assertTrue(curlyOutput.contains("Kinh đô King Xlen đing"), "Unexpected output: $curlyOutput")
        assertTrue(curlyOutput.contains("du a oen căm!"), "Unexpected output: $curlyOutput")
    }

    @Test
    fun appliesRuleBasedFallbackForUnknownForeignProperNames() {
        val output = transliterator.transliterate("Zefran và Jaxon sống ở Shadowness.")
        assertFalse(output.contains("Zefran"), "Unexpected output: $output")
        assertFalse(output.contains("Jaxon"), "Unexpected output: $output")
        assertTrue(output.contains("Dephran"), "Unexpected output: $output")
        assertTrue(output.contains("Giaxon"), "Unexpected output: $output")
    }

    @Test
    fun protectsVietnameseStopWordsAndAccentedWords() {
        val sentences = listOf(
            "Anh ấy đang đi làm.",
            "Em có khỏe không?",
            "Minh và Nam vừa mới về.",
            "Con gái của Lan đang học bài.",
            "Khi nào chúng ta đi xem phim?",
            "Nhưng trong nhà có người Việt Nam.",
        )

        for (sentence in sentences) {
            assertEquals(sentence, transliterator.transliterate(sentence))
        }
    }

    @Test
    fun preservesInlineUrlsEmailsPunctuationAndBlankInput() {
        val input = "Xem tại https://nuvio.tv/watch?v=123 hoặc liên hệ admin@nuvio.tv, Tony Stark nhé!"
        val output = transliterator.transliterate(input)
        assertTrue(output.contains("https://nuvio.tv/watch?v=123"), "Unexpected output: $output")
        assertTrue(output.contains("admin@nuvio.tv,"), "Unexpected output: $output")
        assertTrue(output.contains("Tô ni Xtác nhé!"), "Unexpected output: $output")
        assertEquals("", transliterator.transliterate(""))
        assertEquals("   ", transliterator.transliterate("   "))
    }

    @Test
    fun resolvesVietnameseTtsTargetForPiperAndSystemVoices() {
        val voices = listOf(
            SystemVoiceInfo(name = "vi-vn-x-gft-local", displayName = "Giọng gft", locale = "vi-VN", isNetworkRequired = false),
            SystemVoiceInfo(name = "en-us-x-sfg-local", displayName = "English US", locale = "en-US", isNetworkRequired = false),
        )

        assertTrue(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.PIPER_ONNX, systemVoiceName = "en-us-x-sfg-local"),
                availableVoices = voices,
            ),
        )
        assertTrue(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.SYSTEM, systemVoiceName = null),
                availableVoices = voices,
            ),
        )
        assertTrue(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.SYSTEM, systemVoiceName = "vi-vn-x-gft-local"),
                availableVoices = voices,
            ),
        )
        assertFalse(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.SYSTEM, systemVoiceName = "en-us-x-sfg-local"),
                availableVoices = voices,
            ),
        )
        // Fallback when availableVoices is not yet populated
        assertFalse(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.SYSTEM, systemVoiceName = "com.apple.voice.compact.en-US.Samantha"),
                availableVoices = emptyList(),
            ),
        )
        assertTrue(
            isVietnameseTtsTarget(
                settings = SubtitleTtsSettings(engineType = SubtitleTtsEngineType.SYSTEM, systemVoiceName = "com.apple.voice.compact.vi-VN.Linh"),
                availableVoices = emptyList(),
            ),
        )
    }
}
