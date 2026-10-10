import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.publish)
}

kotlin {
    android {
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
    jvm("desktop")

    sourceSets {
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
