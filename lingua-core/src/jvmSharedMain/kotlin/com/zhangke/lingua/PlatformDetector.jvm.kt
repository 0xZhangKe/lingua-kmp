package com.zhangke.lingua

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.ConcurrentHashMap

internal actual fun createPlatformDetector(options: DetectorOptions): PlatformDetector = JniDetector(options)

private class JniDetector(options: DetectorOptions) : PlatformDetector {
    private val resource: NativeResource

    init {
        // Initialize the reaper before native allocation, so startup failure cannot leak a handle.
        val queue = NativeReaper.queue
        val handle = LinguaNative.create(
            options.languages.map { it.nativeId }.toIntArray(),
            options.minimumRelativeDistance,
            options.preload,
            options.lowAccuracy,
        )
        check(handle != 0L) { "Native detector creation failed" }
        try {
            resource = NativeResource(this, queue, handle)
            NativeReaper.resources.add(resource)
        } catch (error: Throwable) {
            LinguaNative.destroy(handle)
            throw error
        }
    }

    override fun detectLanguageOf(text: String): Language? = call {
        LinguaNative.detect(it, text).let { id -> if (id == 0) null else Language.fromNativeId(id) }
    }
    override fun detectLanguagesInParallelOf(texts: List<String>): List<Language?> = call {
        LinguaNative.detectParallel(it, texts.toTypedArray()).map { id -> if (id == 0) null else Language.fromNativeId(id) }
    }
    override fun computeLanguageConfidence(text: String, language: Language): Double = call {
        LinguaNative.confidence(it, text, language.nativeId)
    }
    override fun computeLanguageConfidenceValues(text: String): List<LanguageConfidence> = call {
        decodeValues(LinguaNative.values(it, text))
    }
    override fun computeLanguageConfidenceInParallel(texts: List<String>, language: Language): List<Double> = call {
        LinguaNative.confidenceParallel(it, texts.toTypedArray(), language.nativeId).toList()
    }
    override fun computeLanguageConfidenceValuesInParallel(texts: List<String>): List<List<LanguageConfidence>> = call {
        LinguaNative.valuesParallel(it, texts.toTypedArray()).map(::decodeValues)
    }
    override fun detectMultipleLanguagesOf(text: String): List<DetectionResult> = call {
        decodeSegments(LinguaNative.multiple(it, text))
    }
    override fun detectMultipleLanguagesInParallelOf(texts: List<String>): List<List<DetectionResult>> = call {
        LinguaNative.multipleParallel(it, texts.toTypedArray()).map(::decodeSegments)
    }
    override fun unloadLanguageModels(): Unit = call { LinguaNative.unload(it) }
    private fun <T> call(block: (Long) -> T): T = try {
        resource.call(block)
    } finally {
        // Java 8 / Android 23 reachability fence: keep the owner alive through the native call.
        synchronized(this) { }
    }

    override fun close(): Unit = resource.release()
}

private fun decodeValues(values: DoubleArray): List<LanguageConfidence> {
    check(values.size % 2 == 0) { "Invalid confidence payload" }
    return values.indices.step(2).map { LanguageConfidence(Language.fromNativeId(values[it].toInt()), values[it + 1]) }
}

private fun decodeSegments(values: IntArray): List<DetectionResult> {
    check(values.size % 4 == 0) { "Invalid segmentation payload" }
    return values.indices.step(4).map {
        DetectionResult(values[it], values[it + 1], values[it + 2], Language.fromNativeId(values[it + 3]))
    }
}

// Phantom references never retain their owner. The same lock guards calls, close and GC cleanup.
private class NativeResource(
    owner: JniDetector,
    queue: ReferenceQueue<JniDetector>,
    private var handle: Long,
) : PhantomReference<JniDetector>(owner, queue) {
    @Synchronized
    fun <T> call(block: (Long) -> T): T {
        check(handle != 0L) { "LanguageDetector is closed" }
        return block(handle)
    }

    @Synchronized
    fun release() {
        if (handle == 0L) return
        LinguaNative.destroy(handle)
        handle = 0L
        clear()
        NativeReaper.resources.remove(this)
    }
}

private object NativeReaper {
    val queue = ReferenceQueue<JniDetector>()
    val resources: MutableSet<NativeResource> = ConcurrentHashMap.newKeySet()

    init {
        Thread({
            while (true) {
                try {
                    (queue.remove() as NativeResource).release()
                } catch (_: InterruptedException) {
                    // This daemon owns no application work and must keep servicing the queue.
                } catch (error: Exception) {
                    reportNativeCleanupFailure(error)
                }
            }
        }, "lingua-native-cleanup").apply {
            isDaemon = true
            start()
        }
    }
}
