package com.zhangke.lingua

// JNI class and method names must remain identical on Android and Desktop.
internal object LinguaNative {
    init { loadNativeLibrary() }

    @JvmStatic external fun create(
        languages: IntArray,
        distance: Double,
        preload: Boolean,
        lowAccuracy: Boolean,
    ): Long
    @JvmStatic external fun destroy(handle: Long)
    @JvmStatic external fun unload(handle: Long)
    @JvmStatic external fun detect(handle: Long, text: String): Int
    @JvmStatic external fun detectParallel(handle: Long, text: Array<String>): IntArray
    @JvmStatic external fun confidence(handle: Long, text: String, language: Int): Double
    @JvmStatic external fun confidenceParallel(handle: Long, text: Array<String>, language: Int): DoubleArray
    @JvmStatic external fun values(handle: Long, text: String): DoubleArray
    @JvmStatic external fun valuesParallel(handle: Long, text: Array<String>): Array<DoubleArray>
    @JvmStatic external fun multiple(handle: Long, text: String): IntArray
    @JvmStatic external fun multipleParallel(handle: Long, text: Array<String>): Array<IntArray>
    @JvmStatic external fun writeModelFiles(
        input: String,
        output: String,
        language: Int,
        charClass: String,
    )
    @JvmStatic external fun writeTestDataFiles(
        input: String,
        output: String,
        charClass: String,
        maximumLines: Long,
    )
    @JvmStatic external fun writeUniqueNgramFiles(output: String)
    @JvmStatic external fun writeMostCommonNgramFiles(
        output: String,
        languages: IntArray,
        mostCommon: Long,
    )
}

internal expect fun loadNativeLibrary()
internal expect fun reportNativeCleanupFailure(error: Exception)
