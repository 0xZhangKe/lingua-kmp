package com.zhangke.lingua

internal data class DetectorOptions(
    val languages: List<Language>,
    val minimumRelativeDistance: Double,
    val preload: Boolean,
    val lowAccuracy: Boolean,
)

internal expect fun createPlatformDetector(options: DetectorOptions): PlatformDetector

internal interface PlatformDetector : AutoCloseable {
    fun detectLanguageOf(text: String): Language?
    fun detectLanguagesInParallelOf(texts: List<String>): List<Language?>
    fun computeLanguageConfidence(text: String, language: Language): Double
    fun computeLanguageConfidenceValues(text: String): List<LanguageConfidence>
    fun computeLanguageConfidenceInParallel(texts: List<String>, language: Language): List<Double>
    fun computeLanguageConfidenceValuesInParallel(texts: List<String>): List<List<LanguageConfidence>>
    fun detectMultipleLanguagesOf(text: String): List<DetectionResult>
    fun detectMultipleLanguagesInParallelOf(texts: List<String>): List<List<DetectionResult>>
    fun unloadLanguageModels(): Unit
}
