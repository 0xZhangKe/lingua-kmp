# lingua-kmp

Kotlin Multiplatform bindings for **lingua-rs 1.8.0** (75 languages). Android uses
JNI; iOS uses Kotlin/Native cinterop and a Rust static library. Both use the
original Rust models. No Java language-detection implementation is used.

```kotlin
import com.zhangke.lingua.*

val detector = LanguageDetectorBuilder
    .fromAllLanguages()
    .withMinimumRelativeDistance(0.25)
    .build()

try {
    val language = detector.detectLanguageOf("今日はいい天気ですね")
    val confidence = detector.computeLanguageConfidence("Hello world", Language.ENGLISH)
    val values = detector.computeLanguageConfidenceValues("Hello world")
    val sections = detector.detectMultipleLanguagesOf("Hello world. 今日はいい天気ですね")
} finally {
    detector.close()
}
```

`LanguageDetector` implements `AutoCloseable`, so `build().use { detector -> … }`
is also supported. Keep a detector for repeated calls. Operations block; run them
off the main thread on Android and iOS. The Android demo provides an executable
example for every detector method, using a separate detector for each example.

## API

See [the audited API map](docs/lingua-rs-api.md) for the complete upstream mapping.
The API includes language and ISO enums, all builder factories/options, detection,
confidence, mixed-language segmentation, the four native parallel batch methods,
model unloading, and upstream's four file-generation tools.

- `Language` exposes `isoCode6391` / `isoCode6393`, ISO lookup and language/script sets.
- ISO enums have lowercase `code` values and case-insensitive `fromCode` parsing.
- Builder subsets require **at least one** language; duplicates are removed.
- Relative distance must be finite and in `0.0..0.99` (including endpoints).
- Low accuracy mode uses less memory, with lower accuracy for short texts.
- `detectLanguageOf` returns `null` when no language can be reliably detected.
- Confidence values are `LanguageConfidence(language, value)` in descending order.
  Equal-confidence ordering is unspecified, as upstream; empty/unclassifiable text
  can produce all-zero values. A language outside the subset has confidence zero.
- Batch methods preserve input order and invoke upstream's Rayon parallel methods.
- `DetectionResult(startIndex, endIndex, wordCount, language)` uses **UTF-16 indices**,
  with an exclusive end, suitable for `text.substring(startIndex, endIndex)`. Rust's
  original UTF-8 byte offsets are converted, including supplementary characters.

## Ownership, concurrency and errors

Android and iOS serialize calls and `close()` per detector. Parallel batch operations
still use native worker threads; separate detector instances may run concurrently.
`close()` is idempotent and every operation after close throws
`IllegalStateException`. Android uses a phantom-reference daemon and iOS uses a Kotlin/Native cleaner
as fallbacks for forgotten close calls; deterministic `close`/`use` remains preferable.

The Rust registry stores `Arc<Detector>` behind opaque, monotonically increasing
IDs; IDs are never raw pointers or reused. An in-flight operation retains an Arc
if its registry entry is removed. Upstream `LanguageDetector: Send + Sync` is
compile-checked. Its model caches are process-wide `LazyLock<DashMap<…>>` values.
Closing releases the detector, **not** the shared model caches.
`unloadLanguageModels()` removes shared models for the configured languages;
other detectors can reload those models on demand.

All JNI exports catch unwinding Rust panics and translate them to
`IllegalStateException`. Invalid builder arguments, language IDs, null native
inputs and unpaired UTF-16 surrogates produce `IllegalArgumentException`.
Invalid/stale native handles produce `IllegalStateException`. Existing JVM
exceptions, including `OutOfMemoryError`, are preserved. Fallible registry
allocation reports `OutOfMemoryError`. Rust/lingua's infallible allocator may abort
on process-wide OOM; `catch_unwind` cannot recover an allocator abort. No promise
of recovery from native OOM is made.

The file writers use absolute filesystem paths and existing output directories,
may overwrite their output files, and accept UTF-8 input. Paths are not Android
content URIs. `UInt` preserves upstream's `u32` count domain. Argument errors are
validated before writing; I/O errors map to `IllegalStateException`. Unexpected
upstream panics also map to `IllegalStateException`. Generated model files do not
replace the detector's embedded models automatically. Generating unique ngrams
for all 75 languages is an offline, memory-intensive operation.

## Platforms and bridge

- **Android:** JNI implementation, arm64-v8a and x86_64, minSdk 23.
- **iOS Arm64 / iOS Simulator Arm64:** C ABI and Kotlin/Native cinterop, all detector
  and file-writer operations implemented. Builds require full Xcode; the current
  Kotlin/Native configuration targets iOS 15 or later.
- **Desktop JVM:** common models and builder validation compile; `build()` and
  file-writer execution throw `UnsupportedOperationException`.

`native/src/core.rs`, `language.rs` and `writers.rs` are platform-independent.
`jni_bridge.rs` handles JNI conversion and exceptions only. `c_bridge.rs` exposes
that same core through the C interface in `native/include/lingua.h`. iOS builds
exclude JNI and use `src/iosMain` to implement the common Kotlin API.

The C ABI borrows length-delimited UTF-16 strings for each call, preserving NULs
and rejecting malformed surrogates. Every operation returns an owned opaque result;
Kotlin copies its contents and releases it in `finally`. Confidence and segment
rows follow the existing wire mapping, with integers represented exactly as doubles.
Handles remain separate 64-bit integers. Rust errors and unwinding panics cannot
cross the C boundary: argument errors become `IllegalArgumentException`, while
state, panic, and fallible allocation errors become `IllegalStateException` on iOS.
An allocator abort remains unrecoverable.

The private wire ID packs the two lowercase ISO 639-1 ASCII bytes as
`(first << 8) | second`; zero means no detected language. It does not depend on
Rust/Kotlin enum ordinals. Confidence payloads contain alternating ID/value doubles;
segment payloads contain groups of four ints. Nested batches use primitive-array
rows. No JSON or public handle types are involved. Consumer ProGuard rules retain
the JNI class and exported method names.

`tools/languages.json` is the reviewed lingua 1.8.0 metadata manifest. Regenerate
both language bindings with `python3 tools/generate_language_models.py`, followed
by `cargo fmt --manifest-path native/Cargo.toml`. Rust tests cross-check every
language, ISO code and script group against upstream and the generated Kotlin file.

## Build and verification

Prerequisites: JDK 21, Android SDK/NDK, Cargo, cargo-ndk, and the
`aarch64-linux-android` / `x86_64-linux-android` Rust targets.

```sh
cargo test --manifest-path native/Cargo.toml --locked
./gradlew :lingua-core:build
./gradlew :demo:connectedDebugAndroidTest
```

`buildRustAndroid` keeps the existing `cargo ndk → jniLibs → AAR` chain and uses
`--locked`. Both Android ABIs are packaged in `lingua-core/build/outputs/aar/lingua-core.aar`.
Generated `.so` files are ignored by Git; `native/Cargo.lock` is tracked.

`commonTest` runs through Desktop. iOS KLIBs still compile on a Command Line Tools
host; iOS simulator test linking/execution is skipped when the simulator SDK is
absent and enabled when it is installed. Android tests require a connected device
or emulator. They cover detection, Latin-script discrimination, confidence,
parallel APIs, Unicode offsets, lifecycle/concurrency, JNI validation, and file
writers. Full all-language unique-ngram generation is not exercised in routine
tests because of its resource cost.

Upstream 1.8.0 limitation: `TestDataFilesWriter` never terminates when
`maximumLines` is zero or exceeds the input line count. The bridge deliberately
rejects those values with `IllegalArgumentException` before writing. This is a
safety correction to an upstream edge case, not an alternative writer algorithm.

### iOS

Install full Xcode and select its developer directory. Install the Rust targets:

```sh
rustup target add aarch64-apple-ios aarch64-apple-ios-sim
./gradlew :lingua-core:compileKotlinIosArm64 :lingua-core:compileKotlinIosSimulatorArm64
./gradlew :lingua-core:iosSimulatorArm64Test
./gradlew :lingua-core:assembleLinguaCoreReleaseXCFramework
```

Gradle builds the Rust static library separately for each target before cinterop,
then embeds it in the generated interop KLIB. KMP consumers therefore link the Rust
library automatically; they do not need to install Cargo to consume a published KLIB.
For Swift consumers, the last command produces the static
`lingua-core/build/XCFrameworks/release/LinguaCore.xcframework`.
Explicitly close detectors from Swift when finished; expensive methods are synchronous.
Detector, builder validation, and writer entry points declare `@Throws` so their
expected Kotlin exceptions are exported as errors Swift can catch.

The iOS tests cover all detector methods, parallel ordering, empty batches, confidence
values, builder options, UTF-16 offsets including emoji, embedded NULs, invalid
surrogates, close semantics, file generation, and native error/panic translation.
Native C ABI tests also run with `cargo test` on the host without an iOS SDK.

```swift
import LinguaCore

let detector = try LanguageDetectorBuilder.companion.fromAllLanguages().build()
defer { detector.close() }
let language = try detector.detectLanguageOf(text: "今日はいい天気ですね")
```
