package com.zhangke.lingua

/** Reusable mutable builder. Configure on one thread; built detectors are independent. */
public class LanguageDetectorBuilder private constructor(
    private val languages: Set<Language>,
) {
    private var minimumRelativeDistance = 0.0
    private var preload = false
    private var lowAccuracy = false

    init {
        require(languages.isNotEmpty()) { "LanguageDetector needs at least 1 language to choose from" }
    }

    /** Accepts finite distances in 0.0..0.99, as in lingua-rs. */
    @Throws(IllegalArgumentException::class)
    public fun withMinimumRelativeDistance(distance: Double): LanguageDetectorBuilder = apply {
        require(distance in 0.0..0.99) { "Minimum relative distance must lie in between 0.0 and 0.99" }
        minimumRelativeDistance = distance
    }

    public fun withPreloadedLanguageModels(): LanguageDetectorBuilder = apply { preload = true }

    /** Saves memory at the expense of accuracy, especially for texts shorter than 120 characters. */
    public fun withLowAccuracyMode(): LanguageDetectorBuilder = apply { lowAccuracy = true }

    /** Caller owns the result and should close it, preferably with [use]. */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun build(): LanguageDetector = LanguageDetector(
        createPlatformDetector(
            DetectorOptions(languages.toList(), minimumRelativeDistance, preload, lowAccuracy),
        ),
    )

    public companion object {
        public fun fromAllLanguages(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.all())
        public fun fromAllSpokenLanguages(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.allSpokenOnes())
        public fun fromAllLanguagesWithArabicScript(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.allWithArabicScript())
        public fun fromAllLanguagesWithCyrillicScript(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.allWithCyrillicScript())
        public fun fromAllLanguagesWithDevanagariScript(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.allWithDevanagariScript())
        public fun fromAllLanguagesWithLatinScript(): LanguageDetectorBuilder = LanguageDetectorBuilder(Language.allWithLatinScript())
        @Throws(IllegalArgumentException::class)
        public fun fromAllLanguagesWithout(vararg languages: Language): LanguageDetectorBuilder =
            LanguageDetectorBuilder(Language.all() - languages.toSet())
        @Throws(IllegalArgumentException::class)
        public fun fromLanguages(vararg languages: Language): LanguageDetectorBuilder =
            LanguageDetectorBuilder(languages.toSet())
        @Throws(IllegalArgumentException::class)
        public fun fromIsoCodes6391(vararg codes: IsoCode6391): LanguageDetectorBuilder =
            LanguageDetectorBuilder(codes.map(Language::fromIsoCode6391).toSet())
        @Throws(IllegalArgumentException::class)
        public fun fromIsoCodes6393(vararg codes: IsoCode6393): LanguageDetectorBuilder =
            LanguageDetectorBuilder(codes.map(Language::fromIsoCode6393).toSet())
    }
}
