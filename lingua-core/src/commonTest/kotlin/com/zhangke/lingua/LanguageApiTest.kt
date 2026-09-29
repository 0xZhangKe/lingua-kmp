package com.zhangke.lingua

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageApiTest {
    @Test
    fun allLanguagesAndCodesRoundTrip() {
        assertEquals(75, Language.entries.size)
        assertEquals(75, Language.entries.map { it.nativeId }.toSet().size)
        for (language in Language.entries) {
            assertEquals(language, Language.fromIsoCode6391(language.isoCode6391))
            assertEquals(language, Language.fromIsoCode6393(language.isoCode6393))
            assertEquals(language, Language.fromNativeId(language.nativeId))
            assertEquals(language.isoCode6391, IsoCode6391.fromCode(language.isoCode6391.code.uppercase()))
            assertEquals(language.isoCode6393, IsoCode6393.fromCode(language.isoCode6393.code))
        }
        assertFailsWith<IllegalArgumentException> { IsoCode6391.fromCode("xx") }
        assertFailsWith<IllegalArgumentException> { IsoCode6393.fromCode("xxx") }
        assertFailsWith<IllegalStateException> { Language.fromNativeId(0) }
    }

    @Test
    fun groupsMatchUpstreamSemantics() {
        assertEquals(Language.all() - Language.LATIN, Language.allSpokenOnes())
        assertEquals(setOf(Language.ARABIC, Language.PERSIAN, Language.URDU), Language.allWithArabicScript())
        assertEquals(setOf(Language.HINDI, Language.MARATHI), Language.allWithDevanagariScript())
        assertTrue(Language.RUSSIAN in Language.allWithCyrillicScript())
        assertTrue(Language.ENGLISH in Language.allWithLatinScript())
        assertTrue(Language.KOREAN in Language.allWithSingleUniqueScript())
        assertFalse(Language.CHINESE in Language.allWithSingleUniqueScript())
    }

    @Test
    fun builderValidationDoesNotNeedNativeRuntime() {
        LanguageDetectorBuilder.fromAllLanguages()
        LanguageDetectorBuilder.fromAllSpokenLanguages()
        LanguageDetectorBuilder.fromAllLanguagesWithArabicScript()
        LanguageDetectorBuilder.fromAllLanguagesWithCyrillicScript()
        LanguageDetectorBuilder.fromAllLanguagesWithDevanagariScript()
        LanguageDetectorBuilder.fromAllLanguagesWithLatinScript()
        LanguageDetectorBuilder.fromAllLanguagesWithout()
        LanguageDetectorBuilder.fromLanguages(Language.ENGLISH, Language.ENGLISH)
        LanguageDetectorBuilder.fromIsoCodes6391(IsoCode6391.EN)
        LanguageDetectorBuilder.fromIsoCodes6393(IsoCode6393.ENG)
        val builder = LanguageDetectorBuilder.fromLanguages(Language.ENGLISH)
        builder.withMinimumRelativeDistance(0.0).withMinimumRelativeDistance(0.99)
            .withLowAccuracyMode().withPreloadedLanguageModels()
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01, 1.0)) {
            assertFailsWith<IllegalArgumentException> { builder.withMinimumRelativeDistance(invalid) }
        }
        assertFailsWith<IllegalArgumentException> { LanguageDetectorBuilder.fromLanguages() }
        assertFailsWith<IllegalArgumentException> { LanguageDetectorBuilder.fromIsoCodes6391() }
        assertFailsWith<IllegalArgumentException> { LanguageDetectorBuilder.fromIsoCodes6393() }
        assertFailsWith<IllegalArgumentException> {
            LanguageDetectorBuilder.fromAllLanguagesWithout(*Language.entries.toTypedArray())
        }
    }
}
