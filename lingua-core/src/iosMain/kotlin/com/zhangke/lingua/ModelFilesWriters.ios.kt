@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.zhangke.lingua

import com.zhangke.lingua.cinterop.*
import kotlinx.cinterop.memScoped

internal actual fun writeModelFiles(input: String, output: String, language: Language, charClass: String): Unit = memScoped {
    nativeUnit(lingua_write_model(nativeText(input), nativeText(output), language.nativeId, nativeText(charClass)))
}

internal actual fun writeTestDataFiles(input: String, output: String, charClass: String, maximumLines: UInt): Unit = memScoped {
    nativeUnit(lingua_write_test_data(nativeText(input), nativeText(output), nativeText(charClass), maximumLines))
}

internal actual fun writeUniqueNgramFiles(output: String): Unit = memScoped {
    nativeUnit(lingua_write_unique(nativeText(output)))
}

internal actual fun writeMostCommonNgramFiles(output: String, languages: List<Language>, mostCommon: UInt): Unit = memScoped {
    nativeUnit(lingua_write_most_common(nativeText(output), nativeIds(languages), languages.size.toULong(), mostCommon))
}
