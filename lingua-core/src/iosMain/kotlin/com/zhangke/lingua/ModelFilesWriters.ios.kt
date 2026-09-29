package com.zhangke.lingua

internal actual fun writeModelFiles(
    input: String,
    output: String,
    language: Language,
    charClass: String,
): Unit = throw UnsupportedOperationException("lingua-rs file writers are not yet implemented on ios")

internal actual fun writeTestDataFiles(
    input: String,
    output: String,
    charClass: String,
    maximumLines: UInt,
): Unit = throw UnsupportedOperationException("lingua-rs file writers are not yet implemented on ios")

internal actual fun writeUniqueNgramFiles(output: String): Unit = throw UnsupportedOperationException("lingua-rs file writers are not yet implemented on ios")

internal actual fun writeMostCommonNgramFiles(
    output: String,
    languages: List<Language>,
    mostCommon: UInt,
): Unit = throw UnsupportedOperationException("lingua-rs file writers are not yet implemented on ios")
