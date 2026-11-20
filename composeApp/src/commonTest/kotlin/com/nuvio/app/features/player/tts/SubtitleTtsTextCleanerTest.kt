package com.nuvio.app.features.player.tts

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTtsTextCleanerTest {

    @Test
    fun cleanStripsAssOverrideTagsAndHtmlTags() {
        val raw = "{\\an8}{\\pos(192,220)}<i>Xin chào</i> <b>thế giới</b>!"
        assertEquals("Xin chào thế giới!", SubtitleTtsTextCleaner.clean(raw))
    }

    @Test
    fun cleanStripsLiteralNewlinesHardSpacesAndCollapsesSpaces() {
        val raw = "Dòng một\\NDòng hai\\nDòng\\hba    với   nhiều khoảng trắng"
        assertEquals("Dòng một Dòng hai Dòng ba với nhiều khoảng trắng", SubtitleTtsTextCleaner.clean(raw))
    }

    @Test
    fun cleanStripsMusicalNotesAndSdhCues() {
        val raw = "♪ [tiếng cười] Anh đang làm gì vậy? (thở dài) ♫"
        assertEquals("Anh đang làm gì vậy?", SubtitleTtsTextCleaner.clean(raw))
    }

    @Test
    fun cleanStripsLeadingSpeakerDashesAcrossMultipleLines() {
        val raw = "- Chào buổi sáng.\\N- Chào anh, hôm nay thế nào?"
        assertEquals("Chào buổi sáng. Chào anh, hôm nay thế nào?", SubtitleTtsTextCleaner.clean(raw))
    }

    @Test
    fun cleanReturnsEmptyForMusicOnlyOrSdhOnlyCues() {
        assertEquals("", SubtitleTtsTextCleaner.clean("♪ ♫ [nhạc dạo] (tiếng bước chân)"))
        assertEquals("", SubtitleTtsTextCleaner.clean("   "))
    }

    @Test
    fun calculateEffectiveSpeedWhenAutoSpeedDisabledClampsBaseSpeed() {
        assertEquals(1.15f, SubtitleTtsTextCleaner.calculateEffectiveSpeed("Xin chào", 1.15f, autoSpeedEnabled = false))
        assertEquals(0.7f, SubtitleTtsTextCleaner.calculateEffectiveSpeed("Xin chào", 0.5f, autoSpeedEnabled = false))
        assertEquals(2.0f, SubtitleTtsTextCleaner.calculateEffectiveSpeed("Xin chào", 2.5f, autoSpeedEnabled = false))
    }

    @Test
    fun calculateEffectiveSpeedAppliesLengthTiersAndClampsWhenAutoSpeedEnabled() {
        val base = 1.0f
        val shortText = "a".repeat(30)
        val mediumText = "a".repeat(45)
        val longText = "a".repeat(75)
        val veryLongText = "a".repeat(100)

        assertEquals(1.0f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(shortText, base, autoSpeedEnabled = true), 0.001f)
        assertEquals(1.12f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(mediumText, base, autoSpeedEnabled = true), 0.001f)
        assertEquals(1.25f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(longText, base, autoSpeedEnabled = true), 0.001f)
        assertEquals(1.38f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(veryLongText, base, autoSpeedEnabled = true), 0.001f)

        // Clamping checks with default maxAutoSpeed (0.8f..2.1f)
        assertEquals(2.07f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(veryLongText, 1.5f, autoSpeedEnabled = true), 0.001f)
        assertEquals(2.1f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(veryLongText, 1.6f, autoSpeedEnabled = true), 0.001f)
        assertEquals(0.8f, SubtitleTtsTextCleaner.calculateEffectiveSpeed(shortText, 0.7f, autoSpeedEnabled = true), 0.001f)
    }

    @Test
    fun calculateEffectiveSpeedRespectsCustomMaxAutoSpeedBounds() {
        val veryLongText = "a".repeat(100)

        assertEquals(
            1.85f,
            SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = veryLongText,
                baseSpeed = 1.5f,
                autoSpeedEnabled = true,
                maxAutoSpeed = 1.85f,
            ),
            0.001f,
        )
        assertEquals(
            2.346f,
            SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = veryLongText,
                baseSpeed = 1.7f,
                autoSpeedEnabled = true,
                maxAutoSpeed = 2.5f,
            ),
            0.001f,
        )
        assertEquals(
            2.76f,
            SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = veryLongText,
                baseSpeed = 2.0f,
                autoSpeedEnabled = true,
                maxAutoSpeed = 3.5f,
            ),
            0.001f,
        )
        assertEquals(
            1.4f,
            SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = veryLongText,
                baseSpeed = 1.2f,
                autoSpeedEnabled = true,
                maxAutoSpeed = 1.0f,
            ),
            0.001f,
        )
        assertEquals(
            1.9f,
            SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = veryLongText,
                baseSpeed = 1.9f,
                autoSpeedEnabled = true,
                maxAutoSpeed = 1.4f,
            ),
            0.001f,
        )
    }

    @Test
    fun ensureSentenceEndingPunctuationAppendsPeriodOnlyWhenMissing() {
        assertEquals(
            "chào mừng bạn đến với áp nuvio 9 triệu đây là giọng của tôi.",
            SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation("chào mừng bạn đến với áp nuvio 9 triệu đây là giọng của tôi"),
        )
        assertEquals("Xin chào!", SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation("Xin chào!"))
        assertEquals("Ổn chứ?", SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation("Ổn chứ?"))
        assertEquals("Nghe xong.", SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation("Nghe xong.  "))
        assertEquals("", SubtitleTtsTextCleaner.ensureSentenceEndingPunctuation("   "))
    }
}
