package com.zhangke.lingua

internal actual fun writeModelFiles(
    input: String,
    output: String,
    language: Language,
    charClass: String,
): Unit = LinguaNative.writeModelFiles(input, output, language.nativeId, charClass)

internal actual fun writeTestDataFiles(
    input: String,
    output: String,
    charClass: String,
    maximumLines: UInt,
): Unit = LinguaNative.writeTestDataFiles(input, output, charClass, maximumLines.toLong())

internal actual fun writeUniqueNgramFiles(output: String): Unit = LinguaNative.writeUniqueNgramFiles(output)

internal actual fun writeMostCommonNgramFiles(
    output: String,
    languages: List<Language>,
    mostCommon: UInt,
): Unit = LinguaNative.writeMostCommonNgramFiles(output, languages.map { it.nativeId }.toIntArray(), mostCommon.toLong())
