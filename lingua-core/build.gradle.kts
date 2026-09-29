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
    iosArm64()
    iosSimulatorArm64()
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

// A Command Line Tools-only macOS host can compile KLIBs but cannot link/run iOS tests.
// Keep common tests running on Desktop and enable the iOS run whenever its SDK is installed.
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
