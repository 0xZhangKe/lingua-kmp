package com.zhangke.lingua

/** Owns a long-lived native detector. Android/iOS operations and close are serialized per instance.
 * Batch methods still run in parallel inside Rust. Different instances may run concurrently.
 * close is idempotent; all other methods throw IllegalStateException after close.
 * Explicit close/use is recommended; Android/iOS also provide a GC cleanup fallback.
 * Methods are blocking. Call expensive operations off the UI thread.
 */
public class LanguageDetector internal constructor(
    private val platform: PlatformDetector,
) : AutoCloseable {
    /** Returns null when detection is unreliable. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun detectLanguageOf(text: String): Language? = platform.detectLanguageOf(text)

    /** Uses native parallel execution and preserves input order. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun detectLanguagesInParallelOf(texts: List<String>): List<Language?> = platform.detectLanguagesInParallelOf(texts)

    /** Returns zero for a language outside the configured subset. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun computeLanguageConfidence(text: String, language: Language): Double = platform.computeLanguageConfidence(text, language)

    /** Returns all configured languages in descending confidence order. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun computeLanguageConfidenceValues(text: String): List<LanguageConfidence> = platform.computeLanguageConfidenceValues(text)

    /** Native parallel confidence calculation, in input order. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun computeLanguageConfidenceInParallel(texts: List<String>, language: Language): List<Double> = platform.computeLanguageConfidenceInParallel(texts, language)

    /** Native parallel confidence lists, in input order. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun computeLanguageConfidenceValuesInParallel(texts: List<String>): List<List<LanguageConfidence>> = platform.computeLanguageConfidenceValuesInParallel(texts)

    /** Segments mixed-language text; indices use Kotlin UTF-16 units. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun detectMultipleLanguagesOf(text: String): List<DetectionResult> = platform.detectMultipleLanguagesOf(text)

    /** Native parallel segmentation, in input order. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun detectMultipleLanguagesInParallelOf(texts: List<String>): List<List<DetectionResult>> = platform.detectMultipleLanguagesInParallelOf(texts)

    /** Unloads shared cached models for this detector’s languages; affects other detectors too. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun unloadLanguageModels(): Unit = platform.unloadLanguageModels()

    override fun close(): Unit = platform.close()
}
