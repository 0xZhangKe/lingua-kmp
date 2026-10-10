# lingua-kmp

Kotlin Multiplatform bindings for **lingua-rs 1.8.0** (75 languages). Android and Desktop JVM use
JNI; iOS uses Kotlin/Native cinterop and a Rust static library. All use the
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
off the UI thread on Android, iOS, and desktop. The multiplatform demo provides an executable
example for every detector method on Android, iOS, and desktop, using a separate
detector for each example.

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

Android, Desktop, and iOS serialize calls and `close()` per detector. Parallel batch operations
still use native worker threads; separate detector instances may run concurrently.
`close()` is idempotent and every operation after close throws
`IllegalStateException`. Android and Desktop use a phantom-reference daemon; iOS uses a Kotlin/Native cleaner
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
- **Desktop JVM:** JNI implements all detector and file-writer operations. macOS
  ARM64 is verified locally. Build/loading supports macOS, Linux (GNU libc), and
  Windows (MSVC), each with ARM64 and x86_64 native classifiers. A six-job CI
  matrix builds and tests those classifiers on matching host architectures.

`native/src/core.rs`, `language.rs` and `writers.rs` are platform-independent.
`jni_bridge.rs` handles JNI conversion and exceptions for Android and Desktop.
`src/jvmSharedMain` shares JNI declarations, result decoding, file writers, and
serialized ownership across both JVM platforms; platform source sets load the library. `c_bridge.rs` exposes
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
./gradlew :demo:androidApp:connectedDebugAndroidTest
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

### Desktop JVM

Development requires JDK 21, Cargo, the selected Rust target (`rustup target add
<target>`), and the native platform linker: Xcode Command Line Tools on macOS,
GCC on Linux, or Visual Studio C++ Build Tools with the selected architecture on
Windows. The generated JVM classes target Java 11.
Gradle compiles a Rust dynamic library for the current host and stages it as
`natives/<os>-<arch>/<library>`. The loader extracts this classpath resource to a
unique temporary directory and loads it once per class loader. Explicit `close`/`use`
is recommended; a phantom-reference reaper provides a GC cleanup fallback.

```sh
./gradlew :lingua-core:desktopTest :lingua-core:desktopPackagedTest
./gradlew :lingua-core:desktopJar :lingua-core:desktopNativesJar
```

For macOS ARM64, these produce two artifacts under `lingua-core/build/libs/`:

- `lingua-core-desktop-0.0.1.jar`: portable JVM classes, without native binaries.
- `lingua-core-desktop-0.0.1-natives-macos-aarch64.jar`: the host Rust/JNI library.

Both artifacts are attached to the Desktop Maven publication. After publishing,
JVM consumers include the classes and their platform's native classifier:

```kotlin
dependencies {
    implementation("io.github.0xzhangke:lingua-core-desktop:0.0.1")
    runtimeOnly("io.github.0xzhangke:lingua-core-desktop:0.0.1:natives-macos-aarch64")
}
```

KMP consumers can keep the common dependency and add that native classifier to
`desktopMain`. Runtime consumers need neither Cargo nor `java.library.path` setup.
Other recognized classifiers are `natives-macos-x86_64`, `natives-linux-aarch64`,
`natives-linux-x86_64`, `natives-windows-aarch64`, and `natives-windows-x86_64`.
By default a build generates the host JVM architecture's classifier. Select another
architecture on the same OS with `-Plingua.desktopArch=x86_64` or
`-Plingua.desktopArch=aarch64`; `arm64`, `amd64`, and `x64` aliases are accepted.
Install that Rust target and provide a cross linker when needed. Selecting an
architecture does not change the operating system or install a linker.

| OS | ARM64 Rust target | x86_64 Rust target |
| --- | --- | --- |
| macOS | `aarch64-apple-darwin` | `x86_64-apple-darwin` |
| Linux (GNU libc) | `aarch64-unknown-linux-gnu` | `x86_64-unknown-linux-gnu` |
| Windows (MSVC) | `aarch64-pc-windows-msvc` | `x86_64-pc-windows-msvc` |

For example, build the Intel Mac native JAR on an Apple Silicon Mac:

```sh
rustup target add x86_64-apple-darwin
./gradlew -Plingua.desktopOnly=true -Plingua.desktopArch=x86_64 \
  :lingua-core:desktopNativesJar
```

`lingua.desktopOnly=true` excludes mobile targets and the Android demo so desktop
builds do not require Android/iOS SDKs or publication signing credentials. Use the
default configuration for publishing the full KMP library; signing is still required
for publication and signing tasks. Each invocation attaches its selected classifier
to the Desktop publication. CI artifacts are build outputs, not Maven releases;
a release must collect the six classifiers under the same Maven version. Place the
six native JARs in one directory, preserving their filenames, then set
`-Plingua.desktopNativeArtifactsDir=/path/to/collected-jars` for publication.
Gradle requires all six and attaches them to the Desktop Maven publication,
replacing the default single-host classifier. Normal signing requirements apply.

Tests must use a JVM matching the native library's architecture. On Apple Silicon,
Intel tests require Rosetta and an x86_64 JDK/JRE; pass its home directory:

```sh
./gradlew -Plingua.desktopOnly=true -Plingua.desktopArch=x86_64 \
  -Plingua.desktopJavaHome=/path/to/x86_64-jdk/Contents/Home \
  :lingua-core:desktopTest :lingua-core:desktopPackagedTest
```

The [Desktop workflow](.github/workflows/desktop.yml) builds, runs the API/JAR/Rust
tests, and uploads each native classifier separately on six matching runners. Runner
labels follow [GitHub's supported runner matrix](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
Linux artifacts built on Ubuntu 24.04 require a compatible GNU libc system; Alpine
musl, 32-bit x86/ARM, and other architectures are not covered.

`desktopTest` covers detection, all parallel operations, confidence, UTF-16 offsets,
builder options, close/concurrency semantics, file generation, and native errors.
`desktopPackagedTest` reruns the API tests using only packaged production JARs,
checks that the native resource comes from a JAR, and uses a separate JVM to verify
the missing-native-artifact error. Both macOS ARM64 and x86_64 (using an Intel JVM under Rosetta) have been exercised locally.
Linux/Windows execution awaits the CI matrix running on those hosts.


## Multiplatform demo

`demo` uses Compose Multiplatform. The English test UI, inputs, results, theme,
background execution, and examples for all ten `LanguageDetector` methods live
in `demo/src/commonMain`. The entry points only host the shared `App()`:

- Android: `demo/androidApp/src/main/kotlin/.../MainActivity.kt`.
- iOS: `demo/src/iosMain/.../MainViewController.kt`, hosted by the SwiftUI app in
  `demo/iosApp/LinguaDemo.xcodeproj`.
- Desktop: `demo/src/desktopMain/.../main.kt`.

AGP 9 requires a separate Android application module, so `:demo:androidApp`
contains only the Android launcher, packaging/resources, and existing Android
instrumentation tests. `demo` supplies its Android library target as well as the
iOS ARM64, iOS Simulator ARM64, and desktop JVM targets.

The demo consumes `io.github.0xzhangke:lingua-core:0.0.1` from Maven Central,
with the version shared through `linguaCore` in `gradle/libs.versions.toml`.
Desktop adds the published `lingua-core-desktop` native classifier matching
the Gradle JVM's operating system and CPU architecture. Building or running the
demo does not build the local `lingua-core` module or require Cargo/Rust targets;
the published Android AAR and iOS KLIBs already include their native libraries.
The selected desktop native classifier must exist in the published version.

```sh
# Android APK / installation / existing instrumentation tests
./gradlew :demo:androidApp:assembleDebug
./gradlew :demo:androidApp:installDebug
./gradlew :demo:androidApp:connectedDebugAndroidTest

# Desktop window (host native JAR is included automatically)
./gradlew :demo:run

# Execute all ten shared examples and formatting checks on Desktop
./gradlew :demo:desktopTest

# iOS framework compilation with full Xcode selected
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
```

For iOS, open `demo/iosApp/LinguaDemo.xcodeproj` in Xcode and run the `LinguaDemo`
scheme on an ARM64 simulator or device. Its build phase runs
`:demo:embedAndSignAppleFrameworkForXcode` to build/link the shared UI and published
native library automatically. An iOS Simulator runtime is needed to launch on a simulator;
set a signing team in Xcode to run on a physical device. Xcode's Gradle build phase
needs JDK 21 available (`JAVA_HOME` can be set in your local Xcode environment).
No CocoaPods setup is needed.
