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

    workingDir(rootProject.file("native"))

    commandLine(
        "cargo",
        "ndk",
        "-t",
        "arm64-v8a",
        "-t",
        "x86_64",
        "-o",
        outputDir.absolutePath,
        "build",
        "--release",
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
