@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.zhangke.lingua

import com.zhangke.lingua.cinterop.*
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.memScoped
import platform.Foundation.NSLock
import kotlin.native.ref.createCleaner

internal actual fun createPlatformDetector(options: DetectorOptions): PlatformDetector = IosDetector(options)

private class IosDetector(options: DetectorOptions) : PlatformDetector {
    private val resource = NativeResource()
    // Captures only the resource, never this detector or the cleaner itself.
    @Suppress("unused")
    private val cleaner = createCleaner(resource) { it.release() }

    init {
        // Install the cleanup fallback before acquiring a native handle.
        resource.handle = memScoped {
            consumeNativeResult(lingua_create(
                nativeIds(options.languages), options.languages.size.toULong(),
                options.minimumRelativeDistance, if (options.preload) 1 else 0,
                if (options.lowAccuracy) 1 else 0,
            )) { lingua_result_handle(it).also { handle -> check(handle != 0L) } }
        }
    }

    override fun detectLanguageOf(text: String): Language? = call { handle ->
        decodeLanguage(nativeRow(lingua_detect(handle, nativeText(text))).single())
    }

    override fun detectLanguagesInParallelOf(texts: List<String>): List<Language?> = call { handle ->
        nativeRow(lingua_detect_parallel(handle, nativeTexts(texts), texts.size.toULong())).map(::decodeLanguage)
    }

    override fun computeLanguageConfidence(text: String, language: Language): Double = call { handle ->
        nativeRow(lingua_confidence(handle, nativeText(text), language.nativeId)).single()
    }

    override fun computeLanguageConfidenceValues(text: String): List<LanguageConfidence> = call { handle ->
        decodeValues(nativeRow(lingua_values(handle, nativeText(text))))
    }

    override fun computeLanguageConfidenceInParallel(texts: List<String>, language: Language): List<Double> = call { handle ->
        nativeRow(lingua_confidence_parallel(handle, nativeTexts(texts), texts.size.toULong(), language.nativeId))
    }

    override fun computeLanguageConfidenceValuesInParallel(texts: List<String>): List<List<LanguageConfidence>> = call { handle ->
        nativeRows(lingua_values_parallel(handle, nativeTexts(texts), texts.size.toULong())).map(::decodeValues)
    }

    override fun detectMultipleLanguagesOf(text: String): List<DetectionResult> = call { handle ->
        decodeSegments(nativeRow(lingua_multiple(handle, nativeText(text))))
    }

    override fun detectMultipleLanguagesInParallelOf(texts: List<String>): List<List<DetectionResult>> = call { handle ->
        nativeRows(lingua_multiple_parallel(handle, nativeTexts(texts), texts.size.toULong())).map(::decodeSegments)
    }

    private fun <T> call(block: MemScope.(Long) -> T): T = resource.call { handle ->
        memScoped { block(handle) }
    }

    override fun unloadLanguageModels(): Unit = resource.call { nativeUnit(lingua_unload(it)) }
    override fun close(): Unit = resource.release()
}

private fun decodeLanguage(value: Double): Language? =
    if (value == 0.0) null else Language.fromNativeId(value.toInt())

private fun decodeValues(values: List<Double>): List<LanguageConfidence> {
    check(values.size % 2 == 0) { "Invalid confidence payload" }
    return values.indices.step(2).map {
        LanguageConfidence(Language.fromNativeId(values[it].toInt()), values[it + 1])
    }
}

private fun decodeSegments(values: List<Double>): List<DetectionResult> {
    check(values.size % 4 == 0) { "Invalid segmentation payload" }
    return values.indices.step(4).map {
        DetectionResult(values[it].toInt(), values[it + 1].toInt(), values[it + 2].toInt(), Language.fromNativeId(values[it + 3].toInt()))
    }
}

// The same lock serializes calls, explicit close, and GC cleanup per instance.
private class NativeResource {
    private val lock = NSLock()
    var handle: Long = 0

    fun <T> call(block: (Long) -> T): T {
        lock.lock()
        try {
            check(handle != 0L) { "LanguageDetector is closed" }
            return block(handle)
        } finally {
            lock.unlock()
        }
    }

    fun release() {
        lock.lock()
        try {
            if (handle != 0L) {
                nativeUnit(lingua_destroy(handle))
                handle = 0
            }
        } finally {
            lock.unlock()
        }
    }
}
