package com.zhangke.lingua.demo

import com.zhangke.lingua.DetectionResult
import com.zhangke.lingua.Language
import com.zhangke.lingua.LanguageConfidence
import com.zhangke.lingua.LanguageDetectorBuilder

internal enum class DetectorExample(val method: String, val description: String) {
    DETECT("detectLanguageOf", "Detect the language of a single text; returns null when detection is unreliable."),
    DETECT_PARALLEL("detectLanguagesInParallelOf", "Detect each line in parallel and display results in input order."),
    CONFIDENCE("computeLanguageConfidence", "Compute the confidence for the target language in a single text."),
    CONFIDENCE_VALUES("computeLanguageConfidenceValues", "Display confidence values for all configured languages in descending order."),
    CONFIDENCE_PARALLEL("computeLanguageConfidenceInParallel", "Compute the confidence for the target language for each line in parallel."),
    CONFIDENCE_VALUES_PARALLEL("computeLanguageConfidenceValuesInParallel", "Compute confidence values for all languages for each line in parallel."),
    MULTIPLE("detectMultipleLanguagesOf", "Detect language segments in mixed text and display UTF-16 offsets, word counts, and original text."),
    MULTIPLE_PARALLEL("detectMultipleLanguagesInParallelOf", "Detect language segments for each line in parallel."),
    UNLOAD("unloadLanguageModels", "Detect → unload shared models → detect again to verify models can be reloaded."),
    CLOSE("close", "Close twice to verify idempotency and that all other 9 methods reject calls after closing.");

    fun run(text: String, texts: List<String>, language: Language): String =
        LanguageDetectorBuilder.fromAllLanguages().build().use { detector ->
            when (this) {
                DETECT -> detector.detectLanguageOf(text).displayName()
                DETECT_PARALLEL -> detector.detectLanguagesInParallelOf(texts)
                    .mapIndexed { index, value -> "${index + 1}. ${texts[index]} → ${value.displayName()}" }.joinToString("\n")
                CONFIDENCE -> "$language: ${detector.computeLanguageConfidence(text, language).formatted()}"
                CONFIDENCE_VALUES -> detector.computeLanguageConfidenceValues(text).formatConfidences()
                CONFIDENCE_PARALLEL -> detector.computeLanguageConfidenceInParallel(texts, language)
                    .mapIndexed { index, value -> "${index + 1}. ${texts[index]} → $language: ${value.formatted()}" }.joinToString("\n")
                CONFIDENCE_VALUES_PARALLEL -> detector.computeLanguageConfidenceValuesInParallel(texts)
                    .mapIndexed { index, values -> "${index + 1}. ${texts[index]}\n${values.formatConfidences()}" }.joinToString("\n\n")
                MULTIPLE -> detector.detectMultipleLanguagesOf(text).formatSegments(text)
                MULTIPLE_PARALLEL -> detector.detectMultipleLanguagesInParallelOf(texts)
                    .mapIndexed { index, values -> "${index + 1}. ${texts[index]}\n${values.formatSegments(texts[index])}" }.joinToString("\n\n")
                UNLOAD -> {
                    val before = detector.detectLanguageOf(text)
                    detector.unloadLanguageModels()
                    val after = detector.detectLanguageOf(text)
                    check(before == after) { "Detection results differ before and after unloading" }
                    "Passed: models unloaded and detection succeeded again.\nBefore unloading: ${before.displayName()}\nAfter unloading: ${after.displayName()}"
                }
                CLOSE -> {
                    detector.close()
                    detector.close()
                    val calls: List<Pair<String, () -> Any?>> = listOf(
                        DETECT.method to { detector.detectLanguageOf(text) },
                        DETECT_PARALLEL.method to { detector.detectLanguagesInParallelOf(texts) },
                        CONFIDENCE.method to { detector.computeLanguageConfidence(text, language) },
                        CONFIDENCE_VALUES.method to { detector.computeLanguageConfidenceValues(text) },
                        CONFIDENCE_PARALLEL.method to { detector.computeLanguageConfidenceInParallel(texts, language) },
                        CONFIDENCE_VALUES_PARALLEL.method to { detector.computeLanguageConfidenceValuesInParallel(texts) },
                        MULTIPLE.method to { detector.detectMultipleLanguagesOf(text) },
                        MULTIPLE_PARALLEL.method to { detector.detectMultipleLanguagesInParallelOf(texts) },
                        UNLOAD.method to { detector.unloadLanguageModels() },
                    )
                    "Passed: repeated close() succeeded.\n" + calls.joinToString("\n") { (name, call) ->
                        val error = runCatching { call() }.exceptionOrNull()
                        check(error is IllegalStateException) { "$name did not throw the expected IllegalStateException: $error" }
                        "$name: passed (IllegalStateException)"
                    }
                }
            }
        }
}

private fun Language?.displayName(): String = this?.let { "${it.name} (${it.isoCode6391.code})" } ?: "null (unreliable detection)"

// Common Kotlin formatting keeps the same decimal output on all three platforms.
internal fun Double.formatted(decimalPlaces: Int = 6): String {
    if (!isFinite()) return toString()
    require(decimalPlaces in 0..6)
    var factor = 1L
    repeat(decimalPlaces) { factor *= 10 }
    val scaled = kotlin.math.round(this * factor).toLong()
    val sign = if (this < 0) "-" else ""
    val magnitude = kotlin.math.abs(scaled)
    val integer = "$sign${magnitude / factor}"
    return if (decimalPlaces == 0) integer
        else "$integer.${(magnitude % factor).toString().padStart(decimalPlaces, '0')}"
}

private fun List<LanguageConfidence>.formatConfidences(): String =
    if (isEmpty()) "[]" else joinToString("\n") { "${it.language.displayName()}: ${it.value.formatted()}" }

private fun List<DetectionResult>.formatSegments(text: String): String =
    if (isEmpty()) "[] (no language segments)" else joinToString("\n") {
        "${it.language.displayName()} [${it.startIndex}, ${it.endIndex}), word count: ${it.wordCount}\n${text.substring(it.startIndex, it.endIndex)}"
    }

