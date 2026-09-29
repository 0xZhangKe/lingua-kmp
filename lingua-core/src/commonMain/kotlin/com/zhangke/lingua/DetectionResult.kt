package com.zhangke.lingua

/** A contiguous section: [startIndex] inclusive, [endIndex] exclusive, in Kotlin UTF-16 units.
 * These indices can be passed directly to String.substring. [wordCount] follows lingua-rs tokenization.
 */
public data class DetectionResult(
    public val startIndex: Int,
    public val endIndex: Int,
    public val wordCount: Int,
    public val language: Language,
)

/** Confidence in 0.0..1.0. Lists retain lingua-rs's descending confidence order (including ties). */
public data class LanguageConfidence(
    public val language: Language,
    public val value: Double,
)
