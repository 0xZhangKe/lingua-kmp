@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.zhangke.lingua

import kotlinx.cinterop.*
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSLock
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUUID
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import kotlin.test.*

class LanguageDetectorIosTest {
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
        val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 4 }
        val lock = NSLock()
        val failures = mutableListOf<String>()
        try {
            repeat(40) { index ->
                queue.addOperationWithBlock {
                    try {
                        if (index == 20) detector.close()
                        try {
                            assertEquals(Language.JAPANESE, detector.detectLanguageOf("今日はいい天気ですね"))
                        } catch (closed: IllegalStateException) {
                            assertEquals("LanguageDetector is closed", closed.message)
                        }
                    } catch (error: Throwable) {
                        lock.lock()
                        try { failures.add(error.toString()) } finally { lock.unlock() }
                    }
                }
            }
            queue.waitUntilAllOperationsAreFinished()
            assertTrue(failures.isEmpty(), failures.joinToString())
        } finally {
            queue.waitUntilAllOperationsAreFinished()
            detector.close()
        }
    }

    @Test
    fun fileWritersAndErrorMapping() {
        val manager = NSFileManager.defaultManager
        val directory = NSTemporaryDirectory() + "lingua-writers-" + NSUUID().UUIDString
        assertTrue(manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null))
        val input = "$directory/input.txt"
        fun writeInput(content: String) {
            val file = checkNotNull(fopen(input, "w"))
            try { check(fputs(content, file) >= 0) } finally { fclose(file) }
        }
        try {
            writeInput("Language detection works with interesting example sentences and several different words.\n")
            TestDataFilesWriter.createAndWriteTestDataFiles(input, directory, "\\p{L}", 1u)
            for (name in listOf("sentences.txt", "single-words.txt", "word-pairs.txt")) {
                assertTrue(manager.fileExistsAtPath("$directory/$name"))
            }
            LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input, directory, Language.ENGLISH, "\\p{L}")
            assertTrue(manager.subpathsAtPath(directory).orEmpty().any { it.toString().endsWith(".fst") })
            MostCommonNgramsWriter.createAndWriteMostCommonNgramFiles(directory, setOf(Language.ENGLISH), 10u)
            assertFailsWith<IllegalArgumentException> { UniqueNgramsWriter.createAndWriteUniqueNgramFiles("relative-path") }
            assertFailsWith<IllegalArgumentException> { LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input, directory, Language.ENGLISH, "[") }
            for (count in listOf(0u, 2u, UInt.MAX_VALUE)) {
                assertFailsWith<IllegalArgumentException> { TestDataFilesWriter.createAndWriteTestDataFiles(input, directory, "\\p{L}", count) }
            }
            writeInput("tiny\n")
            assertFailsWith<IllegalStateException> { TestDataFilesWriter.createAndWriteTestDataFiles(input, directory, "\\p{L}", 1u) }
        } finally {
            manager.removeItemAtPath(directory, error = null)
        }
    }
}
