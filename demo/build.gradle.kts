import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val desktopOs = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "macos"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows"
    System.getProperty("os.name").equals("Linux", ignoreCase = true) -> "linux"
    else -> throw GradleException("Unsupported desktop OS: ${System.getProperty("os.name")}")
}
val desktopArch = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64", "x64" -> "x86_64"
    else -> throw GradleException("Unsupported desktop architecture: ${System.getProperty("os.arch")}")
}

kotlin {
    android {
        namespace = "com.zhangke.lingua.demo.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "LinguaDemo"
            isStatic = true
            binaryOption("bundleId", "com.zhangke.lingua.demo.shared")
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.lingua.core)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            runtimeOnly(project.dependencies.variantOf(libs.lingua.core.desktop) {
                classifier("natives-$desktopOs-$desktopArch")
            })
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.zhangke.lingua.demo.MainKt"
    }
}
