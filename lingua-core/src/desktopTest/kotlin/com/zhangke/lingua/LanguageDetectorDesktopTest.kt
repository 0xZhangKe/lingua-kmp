package com.zhangke.lingua

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LanguageDetectorDesktopTest {
    private fun builder() = LanguageDetectorBuilder.fromLanguages(
        Language.ENGLISH, Language.FRENCH, Language.JAPANESE, Language.CHINESE,
    )

    @Test
    fun detectionConfidenceAndAllParallelOperations() {
        builder().build().use { detector ->
            val texts = listOf("Hello, how are you today?", "Bonjour, comment allez-vous aujourd’hui ?", "今日はいい天気ですね", "这是一个用于测试语言识别的中文句子。", "")
            val expected = listOf(Language.ENGLISH, Language.FRENCH, Language.JAPANESE, Language.CHINESE, null)
            assertEquals(expected, texts.map(detector::detectLanguageOf))
            assertEquals(expected, detector.detectLanguagesInParallelOf(texts))
            val values = detector.computeLanguageConfidenceValues(texts[0])
            assertEquals(Language.ENGLISH, values.first().language)
            assertEquals(4, values.size)
            assertEquals(1.0, values.sumOf { it.value }, 1e-6)
            assertTrue(values.zipWithNext().all { (a, b) -> a.value >= b.value })
            assertEquals(values.first().value, detector.computeLanguageConfidence(texts[0], Language.ENGLISH), 1e-12)
            assertEquals(0.0, detector.computeLanguageConfidence(texts[0], Language.GERMAN))
            texts.zip(detector.computeLanguageConfidenceInParallel(texts, Language.ENGLISH)).forEach { (text, actual) ->
                assertEquals(detector.computeLanguageConfidence(text, Language.ENGLISH), actual, 1e-12)
            }
            // Ties and floating-point rounding can differ across separate calls.
            texts.zip(detector.computeLanguageConfidenceValuesInParallel(texts)).forEach { (text, actual) ->
                val expectedValues = detector.computeLanguageConfidenceValues(text).associate { it.language to it.value }
                assertEquals(expectedValues.keys, actual.map { it.language }.toSet())
                actual.forEach { assertEquals(expectedValues.getValue(it.language), it.value, 1e-12) }
            }
            assertEquals(texts.map(detector::detectMultipleLanguagesOf), detector.detectMultipleLanguagesInParallelOf(texts))
            assertEquals(emptyList(), detector.detectLanguagesInParallelOf(emptyList()))
            assertEquals(emptyList(), detector.computeLanguageConfidenceInParallel(emptyList(), Language.ENGLISH))
            assertEquals(emptyList(), detector.computeLanguageConfidenceValuesInParallel(emptyList()))
            assertEquals(emptyList(), detector.detectMultipleLanguagesInParallelOf(emptyList()))
            detector.unloadLanguageModels()
            assertEquals(Language.ENGLISH, detector.detectLanguageOf(texts[0]))
        }
    }

    @Test
    fun utf16SegmentsEmbeddedNulAndMalformedSurrogates() {
        builder().build().use { detector ->
            val text = "😀 上海大学是一个好大学. It is such a great university."
            val sections = detector.detectMultipleLanguagesOf(text)
            assertTrue(sections.size >= 2)
            assertEquals(0, sections.first().startIndex)
            assertEquals(text.length, sections.last().endIndex)
            assertEquals(text, sections.joinToString("") { text.substring(it.startIndex, it.endIndex) })
            assertTrue(sections.any { it.language == Language.ENGLISH })
            assertTrue(sections.any { it.language == Language.CHINESE })
            assertTrue(sections.all { it.wordCount > 0 })
            assertTrue(detector.detectMultipleLanguagesOf("").isEmpty())
            detector.detectLanguageOf("Hello\u0000 world 😀")
            assertFailsWith<IllegalArgumentException> { detector.detectLanguageOf("\uD800") }
            assertFailsWith<IllegalArgumentException> { detector.detectLanguagesInParallelOf(listOf("ok", "\uDC00")) }
        }
    }

    @Test
    fun closeIsIdempotentAndRejectsEveryOperation() {
        val detector = builder().build()
        detector.close()
        detector.close()
        val calls: List<() -> Any?> = listOf(
            { detector.detectLanguageOf("hello") },
            { detector.detectLanguagesInParallelOf(emptyList()) },
            { detector.computeLanguageConfidence("hello", Language.ENGLISH) },
            { detector.computeLanguageConfidenceValues("hello") },
            { detector.computeLanguageConfidenceInParallel(emptyList(), Language.ENGLISH) },
            { detector.computeLanguageConfidenceValuesInParallel(emptyList()) },
            { detector.detectMultipleLanguagesOf("hello") },
            { detector.detectMultipleLanguagesInParallelOf(emptyList()) },
            { detector.unloadLanguageModels() },
        )
        calls.forEach { call ->
            assertEquals("LanguageDetector is closed", assertFailsWith<IllegalStateException> { call() }.message)
        }
    }

    @Test
    fun builderOptionsAndIndependentOwnership() {
        LanguageDetectorBuilder.fromIsoCodes6391(IsoCode6391.EN, IsoCode6391.FR)
            .withPreloadedLanguageModels().withLowAccuracyMode().withMinimumRelativeDistance(0.99)
            .build().use { detector ->
                assertNull(detector.detectLanguageOf("prologue"))
                assertEquals(Language.ENGLISH, detector.detectLanguageOf(
                    "This is a long English sentence about language detection. We are testing the ability of the detector to recognize English words reliably in low accuracy mode.",
                ))
            }
        val builder = LanguageDetectorBuilder.fromIsoCodes6393(IsoCode6393.JPN)
        builder.build().use { first ->
            builder.build().use { second ->
                first.close()
                assertEquals(Language.JAPANESE, second.detectLanguageOf("今日はいい天気ですね"))
            }
        }
    }

    @Test
    fun simultaneousCallsAndCloseAreSafe() {
        val detector = builder().build()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val calls = (0 until 40).map { index -> Callable {
                if (index == 20) detector.close()
                try {
                    assertEquals(Language.JAPANESE, detector.detectLanguageOf("今日はいい天気ですね"))
                } catch (closed: IllegalStateException) {
                    assertEquals("LanguageDetector is closed", closed.message)
                }
            } }
            pool.invokeAll(calls).forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            detector.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun fileWritersAndErrorMapping() {
        val directory = Files.createTempDirectory("lingua-desktop-writers-").toFile()
        val input = directory.resolve("input.txt")
        try {
            input.writeText("Language detection works with interesting example sentences and several different words.\n")
            TestDataFilesWriter.createAndWriteTestDataFiles(input.path, directory.path, "\\p{L}", 1u)
            for (name in listOf("sentences.txt", "single-words.txt", "word-pairs.txt")) {
                assertTrue(directory.resolve(name).length() > 0)
            }
            LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input.path, directory.path, Language.ENGLISH, "\\p{L}")
            assertTrue(directory.walkTopDown().any { it.extension == "fst" })
            MostCommonNgramsWriter.createAndWriteMostCommonNgramFiles(directory.path, setOf(Language.ENGLISH), 10u)
            assertFailsWith<IllegalArgumentException> { UniqueNgramsWriter.createAndWriteUniqueNgramFiles("relative-path") }
            assertFailsWith<IllegalArgumentException> { LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input.path, directory.path, Language.ENGLISH, "[") }
            for (count in listOf(0u, 2u, UInt.MAX_VALUE)) {
                assertFailsWith<IllegalArgumentException> { TestDataFilesWriter.createAndWriteTestDataFiles(input.path, directory.path, "\\p{L}", count) }
            }
            input.writeText("tiny\n")
            assertFailsWith<IllegalStateException> { TestDataFilesWriter.createAndWriteTestDataFiles(input.path, directory.path, "\\p{L}", 1u) }
            input.writeBytes(byteArrayOf(0xFF.toByte()))
            assertFailsWith<IllegalArgumentException> { TestDataFilesWriter.createAndWriteTestDataFiles(input.path, directory.path, "\\p{L}", 1u) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rawJniRejectsInvalidArgumentsAndStaleHandles() {
        assertFailsWith<IllegalStateException> { LinguaNative.detect(0, "hello") }
        assertFailsWith<IllegalArgumentException> { LinguaNative.create(intArrayOf(0), 0.0, false, false) }
        assertFailsWith<IllegalArgumentException> { LinguaNative.create(intArrayOf(Language.ENGLISH.nativeId), Double.NaN, false, false) }
        val handle = LinguaNative.create(intArrayOf(Language.JAPANESE.nativeId, Language.CHINESE.nativeId), 0.0, false, false)
        try {
            assertFailsWith<IllegalArgumentException> { LinguaNative.confidence(handle, "hello", 0) }
            assertEquals(1000, LinguaNative.detectParallel(handle, Array(1000) { "今日はいい天気ですね" }).size)
        } finally {
            LinguaNative.destroy(handle)
        }
        assertFailsWith<IllegalStateException> { LinguaNative.detect(handle, "hello") }
        assertFailsWith<IllegalStateException> { LinguaNative.destroy(handle) }
    }
}
