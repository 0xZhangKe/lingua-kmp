package com.zhangke.lingua

/** Upstream model-generation tool. Uses absolute filesystem paths, not Android content URIs.
 * Output directory must already exist. Existing output files may be overwritten.
 * Invalid arguments throw IllegalArgumentException; I/O failures throw IllegalStateException.
 * Blocking and potentially memory-intensive; run off the UI thread.
 */
public object LanguageModelFilesWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createAndWriteLanguageModelFiles(
        inputFilePath: String,
        outputDirectoryPath: String,
        language: Language,
        charClass: String,
    ): Unit = writeModelFiles(inputFilePath, outputDirectoryPath, language, charClass)
}

/** Creates sentences.txt, single-words.txt and word-pairs.txt with upstream tokenization.
 * Paths, overwriting and errors follow [LanguageModelFilesWriter]. Input must be UTF-8.
 * maximumLines must be in 1..input line count: other values hang in upstream 1.8.0 and are rejected.
 */
public object TestDataFilesWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createAndWriteTestDataFiles(
        inputFilePath: String,
        outputDirectoryPath: String,
        charClass: String,
        maximumLines: UInt,
    ): Unit = writeTestDataFiles(inputFilePath, outputDirectoryPath, charClass, maximumLines)
}

/** Generates unique ngrams for all built-in languages. Can require substantial memory.
 * Paths, overwriting and errors follow [LanguageModelFilesWriter].
 */
public object UniqueNgramsWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createAndWriteUniqueNgramFiles(outputDirectoryPath: String): Unit =
        writeUniqueNgramFiles(outputDirectoryPath)
}

/** Generates common ngrams for a nonempty language subset.
 * Paths, overwriting and errors follow [LanguageModelFilesWriter].
 */
public object MostCommonNgramsWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun createAndWriteMostCommonNgramFiles(
        outputDirectoryPath: String,
        languages: Set<Language>,
        mostCommon: UInt,
    ) {
        require(languages.isNotEmpty()) { "Set of languages must not be empty" }
        require(mostCommon > 0u) { "Amount of most common ngrams must be greater than zero" }
        writeMostCommonNgramFiles(outputDirectoryPath, languages.toList(), mostCommon)
    }
}

internal expect fun writeModelFiles(
    input: String,
    output: String,
    language: Language,
    charClass: String,
)
internal expect fun writeTestDataFiles(
    input: String,
    output: String,
    charClass: String,
    maximumLines: UInt,
)
internal expect fun writeUniqueNgramFiles(output: String)
internal expect fun writeMostCommonNgramFiles(
    output: String,
    languages: List<Language>,
    mostCommon: UInt,
)
