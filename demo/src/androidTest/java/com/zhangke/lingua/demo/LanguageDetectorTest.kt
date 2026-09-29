package com.zhangke.lingua.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangke.lingua.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LanguageDetectorTest {
    private fun builder() = LanguageDetectorBuilder.fromLanguages(
        Language.ENGLISH,
        Language.FRENCH,
        Language.JAPANESE,
        Language.CHINESE,
    )

    @Test
    fun languagesConfidenceAndParallelOperations() {
        builder().build().use { detector ->
            val texts = listOf(
                "Hello, how are you today?",
                "Bonjour, comment allez-vous aujourd’hui ?",
                "今日はいい天気ですね",
                "这是一个用于测试语言识别的中文句子。",
                "",
            )
            val expected = listOf(Language.ENGLISH, Language.FRENCH, Language.JAPANESE, Language.CHINESE, null)
            assertEquals(expected, texts.map(detector::detectLanguageOf))
            assertEquals(expected, detector.detectLanguagesInParallelOf(texts))
            val values = detector.computeLanguageConfidenceValues(texts[0])
            assertEquals(Language.ENGLISH, values.first().language)
            assertEquals(4, values.size)
            assertEquals(1.0, values.sumOf { it.value }, 0.000001)
            assertTrue(values.zipWithNext().all { (a, b) -> a.value >= b.value })
            assertEquals(values.first().value, detector.computeLanguageConfidence(texts[0], Language.ENGLISH), 1e-12)
            assertEquals(0.0, detector.computeLanguageConfidence(texts[0], Language.GERMAN), 0.0)
            texts.zip(detector.computeLanguageConfidenceValuesInParallel(texts)).forEach { (text, actual) ->
                val expectedValues = detector.computeLanguageConfidenceValues(text).associate { it.language to it.value }
                assertEquals(expectedValues.keys, actual.map { it.language }.toSet())
                actual.forEach { assertEquals(expectedValues.getValue(it.language), it.value, 1e-12) }
            }
            texts.zip(detector.computeLanguageConfidenceInParallel(texts, Language.ENGLISH)).forEach { (text, actual) ->
                assertEquals(detector.computeLanguageConfidence(text, Language.ENGLISH), actual, 1e-12)
            }
            assertEquals(texts.map(detector::detectMultipleLanguagesOf), detector.detectMultipleLanguagesInParallelOf(texts))
            assertEquals(emptyList<Language?>(), detector.detectLanguagesInParallelOf(emptyList()))
            assertEquals(emptyList<List<LanguageConfidence>>(), detector.computeLanguageConfidenceValuesInParallel(emptyList()))
            assertEquals(emptyList<List<DetectionResult>>(), detector.detectMultipleLanguagesInParallelOf(emptyList()))
            assertEquals(emptyList<Double>(), detector.computeLanguageConfidenceInParallel(emptyList(), Language.ENGLISH))
            detector.unloadLanguageModels()
            assertEquals(Language.ENGLISH, detector.detectLanguageOf(texts[0]))
        }
    }

    @Test
    fun segmentationUsesUtf16IncludingSupplementaryCharacters() {
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
            // JNI preserves embedded NUL and supplementary code points; rejects malformed UTF-16.
            detector.detectLanguageOf("Hello\u0000 world 😀")
            assertThrows(IllegalArgumentException::class.java) { detector.detectLanguageOf("\uD800") }
            assertThrows(IllegalArgumentException::class.java) { detector.detectLanguagesInParallelOf(listOf("ok", "\uDC00")) }
        }
    }

    @Test
    fun optionsSubsetAndBuilderReuse() {
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
    fun closeIsIdempotentAndAllOperationsRejectClosedDetector() {
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
        calls.forEach { call -> assertThrows(IllegalStateException::class.java) { call() } }
    }

    @Test
    fun rawJniBoundaryRejectsInvalidInputAndStaleHandles() {
        val native = Class.forName("com.zhangke.lingua.LinguaNative")
        fun invoke(name: String, vararg args: Any?): Any? {
            val method = native.declaredMethods.single { it.name == name }
            try {
                return method.invoke(null, *args)
            } catch (error: java.lang.reflect.InvocationTargetException) {
                throw error.targetException
            }
        }
        assertThrows(IllegalStateException::class.java) { invoke("detect", 0L, "hello") }
        assertThrows(IllegalStateException::class.java) { invoke("destroy", -1L) }
        assertThrows(IllegalArgumentException::class.java) { invoke("create", intArrayOf(0), 0.0, false, false) }
        assertThrows(IllegalArgumentException::class.java) { invoke("create", intArrayOf(25966), Double.NaN, false, false) }
        val handle = invoke("create", intArrayOf(27233, 31336), 0.0, false, false) as Long
        try {
            assertThrows(IllegalArgumentException::class.java) { invoke("detect", handle, null) }
            assertThrows(IllegalArgumentException::class.java) { invoke("confidence", handle, "hello", 0) }
            val batch = Array(1000) { "今日はいい天気ですね" }
            assertEquals(1000, (invoke("detectParallel", handle, batch) as IntArray).size)
        } finally {
            invoke("destroy", handle)
        }
        assertThrows(IllegalStateException::class.java) { invoke("detect", handle, "hello") }
        assertThrows(IllegalStateException::class.java) { invoke("destroy", handle) }
    }

    @Test
    fun upstreamFileWritersAndValidation() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "lingua-writer-${System.nanoTime()}").apply { mkdirs() }
        try {
            val input = java.io.File(directory, "input.txt").apply {
                writeText("Language detection works with interesting example sentences and several different words.\n")
            }
            val output = java.io.File(directory, "output").apply { mkdirs() }
            TestDataFilesWriter.createAndWriteTestDataFiles(input.path, output.path, "\\p{L}", 1u)
            assertTrue(java.io.File(output, "sentences.txt").readText().contains("Language"))
            assertTrue(java.io.File(output, "single-words.txt").length() > 0)
            assertTrue(java.io.File(output, "word-pairs.txt").length() > 0)
            LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input.path, output.path, Language.ENGLISH, "\\p{L}")
            assertTrue(output.walkTopDown().any { it.extension == "fst" })
            MostCommonNgramsWriter.createAndWriteMostCommonNgramFiles(output.path, setOf(Language.ENGLISH), 10u)
            assertThrows(IllegalArgumentException::class.java) {
                UniqueNgramsWriter.createAndWriteUniqueNgramFiles("relative-path")
            }
            assertThrows(IllegalArgumentException::class.java) {
                LanguageModelFilesWriter.createAndWriteLanguageModelFiles(input.path, output.path, Language.ENGLISH, "[")
            }
            for (invalidCount in listOf(0u, 2u, UInt.MAX_VALUE)) {
                assertThrows(IllegalArgumentException::class.java) {
                    TestDataFilesWriter.createAndWriteTestDataFiles(input.path, output.path, "\\p{L}", invalidCount)
                }
            }
            // Upstream panics when no >=5-character words can form a pair.
            // Verify that a real Rust panic becomes a JVM exception, not a process crash.
            input.writeText("tiny\n")
            assertThrows(IllegalStateException::class.java) {
                TestDataFilesWriter.createAndWriteTestDataFiles(input.path, output.path, "\\p{L}", 1u)
            }
            input.writeBytes(byteArrayOf(0xFF.toByte()))
            assertThrows(IllegalArgumentException::class.java) {
                TestDataFilesWriter.createAndWriteTestDataFiles(input.path, output.path, "\\p{L}", 1u)
            }
        } finally {
            directory.deleteRecursively()
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
}
