import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.publish)
}

// Build one architecture per invocation; the portable JVM JAR contains no binaries.
// Desktop-only CI does not configure Android targets or require mobile SDKs.
val desktopOnly = providers.gradleProperty("lingua.desktopOnly").map(String::toBoolean).getOrElse(false)
if (!desktopOnly) pluginManager.apply("com.android.kotlin.multiplatform.library")

fun normalizedDesktopArch(value: String): String = when (value.lowercase()) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64", "x64" -> "x86_64"
    else -> throw GradleException("Unsupported desktop architecture: $value (expected aarch64 or x86_64)")
}
val desktopOs = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "macos"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows"
    System.getProperty("os.name").equals("Linux", ignoreCase = true) -> "linux"
    else -> throw GradleException("Unsupported desktop build OS: ${System.getProperty("os.name")}")
}
val desktopArch = normalizedDesktopArch(
    providers.gradleProperty("lingua.desktopArch").getOrElse(System.getProperty("os.arch")),
)
val desktopPlatform = "$desktopOs-$desktopArch"
val desktopRustTarget = when (desktopOs) {
    "macos" -> "$desktopArch-apple-darwin"
    "windows" -> "$desktopArch-pc-windows-msvc"
    else -> "$desktopArch-unknown-linux-gnu"
}
val desktopLibraryName = when (desktopOs) {
    "macos" -> "liblingua_kmp.dylib"
    "windows" -> "lingua_kmp.dll"
    else -> "liblingua_kmp.so"
}
val desktopRustOutput = layout.buildDirectory.dir("rust/desktop")
val desktopNativeFile = desktopRustOutput.map { it.file("$desktopRustTarget/release/$desktopLibraryName") }
val buildRustDesktop by tasks.registering(Exec::class) {
    workingDir(rootProject.file("native"))
    executable = providers.environmentVariable("CARGO_HOME")
        .map { "$it/bin/cargo${if (desktopOs == "windows") ".exe" else ""}" }
        .getOrElse("${System.getProperty("user.home")}/.cargo/bin/cargo${if (desktopOs == "windows") ".exe" else ""}")
    args("build", "--release", "--locked", "--lib", "--target", desktopRustTarget,
        "--target-dir", desktopRustOutput.get().asFile.absolutePath)
    inputs.dir(rootProject.file("native/src"))
    inputs.file(rootProject.file("native/Cargo.toml"))
    inputs.file(rootProject.file("native/Cargo.lock"))
    inputs.property("rustTarget", desktopRustTarget)
    outputs.file(desktopNativeFile)
}
val stageDesktopNative by tasks.registering(Sync::class) {
    dependsOn(buildRustDesktop)
    from(desktopNativeFile) { into("natives/$desktopPlatform") }
    into(layout.buildDirectory.dir("generated/desktopNativeResources/$desktopPlatform"))
}
val desktopNativesJar by tasks.registering(Jar::class) {
    archiveBaseName.set("${project.name}-desktop")
    archiveClassifier.set("natives-$desktopPlatform")
    from(stageDesktopNative)
}


kotlin {
    if (!desktopOnly) (this as ExtensionAware).extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = "com.zhangke.lingua"
        compileSdk = 37
        minSdk = 23
        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(file("consumer-rules.pro"))
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    if (!desktopOnly) {
        val xcframework = XCFramework("LinguaCore")
        listOf(iosArm64() to "aarch64-apple-ios", iosSimulatorArm64() to "aarch64-apple-ios-sim").forEach { (target, rustTarget) ->
            val rustOutput = layout.buildDirectory.dir("rust/$rustTarget")
            val buildRust = tasks.register<Exec>("buildRust${target.name.replaceFirstChar(Char::uppercaseChar)}") {
                workingDir(rootProject.file("native"))
                executable = providers.environmentVariable("CARGO_HOME")
                    .map { "$it/bin/cargo" }.getOrElse("${System.getProperty("user.home")}/.cargo/bin/cargo")
                args("build", "--release", "--locked", "--lib", "--target", rustTarget,
                    "--target-dir", rustOutput.get().asFile.absolutePath)
                inputs.dir(rootProject.file("native/src"))
                inputs.file(rootProject.file("native/Cargo.toml"))
                inputs.file(rootProject.file("native/Cargo.lock"))
                outputs.file(rustOutput.map { it.file("$rustTarget/release/liblingua_kmp.a") })
                doFirst {
                    check(System.getProperty("os.name").contains("Mac")) { "iOS native builds require macOS with Xcode installed" }
                    val sdk = if (rustTarget.endsWith("-sim")) "iphonesimulator" else "iphoneos"
                    val sdkPath = providers.exec {
                        commandLine("/usr/bin/xcrun", "--sdk", sdk, "--show-sdk-path")
                        isIgnoreExitValue = true
                    }
                    check(sdkPath.result.get().exitValue == 0) { "Missing $sdk SDK. Install full Xcode and select it with xcode-select." }
                    environment("SDKROOT", sdkPath.standardOutput.asText.get().trim())
                    environment("IPHONEOS_DEPLOYMENT_TARGET", "15.0")
                }
            }
            val interop = target.compilations.getByName("main").cinterops.create("lingua") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/lingua.def"))
                includeDirs(rootProject.file("native/include"))
                extraOpts("-libraryPath", rustOutput.get().dir("$rustTarget/release").asFile.absolutePath)
            }
            tasks.named(interop.interopProcessingTaskName) {
                dependsOn(buildRust)
                inputs.file(rustOutput.map { it.file("$rustTarget/release/liblingua_kmp.a") })
                inputs.file(rootProject.file("native/include/lingua.h"))
            }
            target.binaries.framework {
                baseName = "LinguaCore"
                isStatic = true
                xcframework.add(this)
            }
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        if (!desktopOnly) getByName("androidMain").dependsOn(jvmSharedMain)
        getByName("desktopMain").apply {
            dependsOn(jvmSharedMain)
            resources.srcDir(stageDesktopNative.map { it.destinationDir })
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val buildRustAndroid by tasks.registering(Exec::class) {
    val outputDir = project.file(
        "src/androidMain/jniLibs",
    )

    val cargoHome = providers.environmentVariable("CARGO_HOME")
        .orElse(
            providers.provider {
                "${System.getProperty("user.home")}/.cargo"
            },
        )

    val cargo = cargoHome.map {
        file("$it/bin/cargo").absolutePath
    }

    workingDir(rootProject.file("native"))

    executable = cargo.get()

    args(
        "ndk",
        "-t",
        "arm64-v8a",
        "-t",
        "x86_64",
        "-o",
        outputDir.absolutePath,
        "build",
        "--release",
        "--locked",
    )

    inputs.dir(rootProject.file("native/src"))
    inputs.file(rootProject.file("native/Cargo.toml"))
    inputs.file(rootProject.file("native/Cargo.lock"))

    outputs.dir(outputDir)
}

tasks.configureEach {
    if (
        name.contains("Jni", ignoreCase = true) &&
        name.contains("Merge", ignoreCase = true)
    ) {
        dependsOn(buildRustAndroid)
    }
}

tasks.named("clean") {
    doLast {
        delete(
            "src/androidMain/jniLibs",
        )
    }
}

// Test execution requires an installed iOS Simulator SDK. Native compilation also
// requires full Xcode; the Rust build task reports missing SDKs explicitly.
val hasIosSimulatorSdk = providers.provider {
    System.getProperty("os.name").contains("Mac", ignoreCase = true) &&
        providers.exec {
            commandLine("/usr/bin/xcrun", "--sdk", "iphonesimulator", "--show-sdk-path")
            isIgnoreExitValue = true
        }.result.get().exitValue == 0
}
tasks.matching {
    it.name == "linkDebugTestIosSimulatorArm64" || it.name == "iosSimulatorArm64Test"
}.configureEach {
    onlyIf("An iOS Simulator SDK is installed") { hasIosSimulatorSdk.get() }
}

// Classpath tests use the staged host binary; published JVM classes stay portable.
tasks.named<Jar>("desktopJar") { exclude("natives/**") }
// A release job can collect CI JARs and attach all six classifiers in one publication.
val desktopNativeArtifactsDir = providers.gradleProperty("lingua.desktopNativeArtifactsDir")
publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "desktop") {
        if (desktopNativeArtifactsDir.isPresent) {
            for (os in listOf("macos", "linux", "windows")) {
                for (arch in listOf("aarch64", "x86_64")) {
                    val nativeClassifier = "natives-$os-$arch"
                    val nativeJar = file("${desktopNativeArtifactsDir.get()}/${project.name}-desktop-${project.version}-$nativeClassifier.jar")
                    check(nativeJar.isFile) { "Missing desktop release artifact: $nativeJar" }
                    artifact(nativeJar) { classifier = nativeClassifier }
                }
            }
        } else {
            artifact(desktopNativesJar)
        }
    }
}

// Execute the same API tests from the published layout, without main classes or
// loose native resources on the classpath, plus an explicit JAR loading check.
val desktopPackagedTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Verifies desktop API and native loading from packaged JARs"
    val testCompilation = kotlin.targets.getByName("desktop").compilations.getByName("test")
    testClassesDirs = testCompilation.output.classesDirs
    classpath = files(testCompilation.output.allOutputs, tasks.named("desktopJar"), desktopNativesJar) +
        configurations.getByName("desktopTestRuntimeClasspath")
    useJUnit()
}
tasks.named<Test>("desktopTest") { exclude("**/DesktopPackagedTest*") }
tasks.named("check") { dependsOn(desktopPackagedTest) }

// Cross-architecture tests need a JVM of the same architecture as the native JAR.
val desktopJavaHome = providers.gradleProperty("lingua.desktopJavaHome")
tasks.withType<Test>().matching { it.name == "desktopTest" || it.name == "desktopPackagedTest" }.configureEach {
    if (desktopJavaHome.isPresent) {
        executable = file("${desktopJavaHome.get()}/bin/java${if (desktopOs == "windows") ".exe" else ""}").absolutePath
    }
    inputs.property("desktopNativePlatform", desktopPlatform)
    doFirst {
        val javaSettings = providers.exec {
            commandLine(executable ?: javaLauncher.get().executablePath.asFile.absolutePath, "-XshowSettings:properties", "-version")
        }.standardError.asText.get()
        val javaArch = Regex("os\\.arch\\s*=\\s*(\\S+)").find(javaSettings)?.groupValues?.get(1)
            ?: throw GradleException("Could not determine desktop test JVM architecture")
        check(normalizedDesktopArch(javaArch) == desktopArch) {
            "Desktop native architecture $desktopArch does not match test JVM $javaArch. " +
                "Set -Plingua.desktopJavaHome to a matching JDK installation."
        }
    }
}
