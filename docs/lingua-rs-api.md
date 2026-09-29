# lingua-rs 1.8.0 API audit

Source of truth: `native/Cargo.lock` resolves `lingua = 1.8.0`; inspected the local
Cargo registry's `lingua-1.8.0/src/{lib,builder,detector,language,isocode,result,writer}.rs`.
The dependency enables upstream's default full language feature set (75 languages).
This inventory is tied to the lockfile, not to remembered or latest documentation.

## Types and language metadata

| Rust | Kotlin |
| --- | --- |
| `Language` | `Language` (75 enum entries, uppercase Kotlin names) |
| `IsoCode639_1`, `IsoCode639_3` | `IsoCode6391`, `IsoCode6393` (75 typed codes each) |
| `Language::all()` | `Language.all()` |
| `all_spoken_ones()` | `allSpokenOnes()` (excludes Latin) |
| `all_with_arabic_script()` | `allWithArabicScript()` |
| `all_with_cyrillic_script()` | `allWithCyrillicScript()` |
| `all_with_devanagari_script()` | `allWithDevanagariScript()` |
| `all_with_latin_script()` | `allWithLatinScript()` |
| `all_with_single_unique_script()` | `allWithSingleUniqueScript()` |
| `from_iso_code_639_1/3()` | `fromIsoCode6391/3()` |
| `iso_code_639_1/3()` | `isoCode6391/3` properties |
| ISO `Display` / `FromStr` | lowercase `code`, `toString()`, case-insensitive `fromCode()` |
| `Vec<(Language, f64)>` | `List<LanguageConfidence>` |
| `DetectionResult` accessors | immutable `DetectionResult` properties |

Language sets follow the actual source alphabet metadata. In particular, Chinese
is not in the single-unique-script set because Japanese also uses Han characters.
Rust-specific serde/strum/FFI traits are not exposed as Kotlin platform details.

## Builder (all 10 factories, all 3 options)

| Rust | Kotlin |
| --- | --- |
| `from_all_languages` | `fromAllLanguages` |
| `from_all_spoken_languages` | `fromAllSpokenLanguages` |
| `from_all_languages_with_arabic_script` | `fromAllLanguagesWithArabicScript` |
| `from_all_languages_with_cyrillic_script` | `fromAllLanguagesWithCyrillicScript` |
| `from_all_languages_with_devanagari_script` | `fromAllLanguagesWithDevanagariScript` |
| `from_all_languages_with_latin_script` | `fromAllLanguagesWithLatinScript` |
| `from_all_languages_without` | `fromAllLanguagesWithout` |
| `from_languages` | `fromLanguages` |
| `from_iso_codes_639_1` | `fromIsoCodes6391` |
| `from_iso_codes_639_3` | `fromIsoCodes6393` |
| `with_minimum_relative_distance` | `withMinimumRelativeDistance` |
| `with_preloaded_language_models` | `withPreloadedLanguageModels` |
| `with_low_accuracy_mode` | `withLowAccuracyMode` |
| `build` | `build` |

Factories accept typed Kotlin varargs. An empty exclusion list is legal; excluding
all languages is not. A one-language detector is supported. NaN and infinities are
rejected, matching Rust's range check. Builders are mutable and reusable; each
build creates an independent native detector with a snapshot of the options.

## Detector (all 9 methods)

| Rust | Kotlin return type |
| --- | --- |
| `detect_language_of` → `detectLanguageOf` | `Language?` |
| `detect_languages_in_parallel_of` → `detectLanguagesInParallelOf` | `List<Language?>` |
| `compute_language_confidence` → `computeLanguageConfidence` | `Double` |
| `compute_language_confidence_in_parallel` → `computeLanguageConfidenceInParallel` | `List<Double>` |
| `compute_language_confidence_values` → `computeLanguageConfidenceValues` | `List<LanguageConfidence>` |
| `compute_language_confidence_values_in_parallel` → `computeLanguageConfidenceValuesInParallel` | `List<List<LanguageConfidence>>` |
| `detect_multiple_languages_of` → `detectMultipleLanguagesOf` | `List<DetectionResult>` |
| `detect_multiple_languages_in_parallel_of` → `detectMultipleLanguagesInParallelOf` | `List<List<DetectionResult>>` |
| `unload_language_models` → `unloadLanguageModels` | `Unit` |

`DetectionResult` exposes `startIndex`, `endIndex`, `wordCount`, `language`.
Indices deliberately adapt Rust UTF-8 byte offsets to Kotlin UTF-16 code units;
word counts and detected languages retain upstream semantics. Upstream floating
point accumulation can vary at the last bit between calls/parallel executions.
Confidence ties retain upstream's unspecified order rather than imposing a new tie-break.

`close()` is the additional Kotlin ownership operation required by native resources.
Rust's detector docs explicitly guarantee multithreaded use; the bridge additionally
compile-checks `Send + Sync` and guards registry lifecycle with `Mutex`/`Arc`.

## Additional public file-generation tools

These are also exported by upstream `lib.rs`, independently of detection:

| Rust type / function | Kotlin |
| --- | --- |
| `LanguageModelFilesWriter::create_and_write_language_model_files` | `LanguageModelFilesWriter.createAndWriteLanguageModelFiles` |
| `TestDataFilesWriter::create_and_write_test_data_files` | `TestDataFilesWriter.createAndWriteTestDataFiles` |
| `UniqueNgramsWriter::create_and_write_unique_ngram_files` | `UniqueNgramsWriter.createAndWriteUniqueNgramFiles` |
| `MostCommonNgramsWriter::create_and_write_most_common_ngram_files` | `MostCommonNgramsWriter.createAndWriteMostCommonNgramFiles` |

Rust `&Path` maps to an absolute path `String`, language sets remain typed, and
`u32` counts map to `UInt`. Rust argument panics are prevented with validation;
I/O errors use `IllegalStateException`. Unexpected panic is contained at JNI.
Wasm-only exports and Python wrapper APIs are not part of this Android/KMP binding.

Upstream 1.8.0 limitation: `TestDataFilesWriter` never terminates when
`maximumLines` is zero or exceeds the input line count. The bridge deliberately
rejects those values with `IllegalArgumentException` before writing. This is a
safety correction to an upstream edge case, not an alternative writer algorithm.
