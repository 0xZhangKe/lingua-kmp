import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
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
            implementation(project(":lingua-core"))
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("desktopMain").dependencies { implementation(compose.desktop.currentOs) }
    }
}

// Local project dependencies use lingua-core's staged native resources. Packaged
// desktop distributions also need the host native JAR on their runtime classpath.
val desktopNatives = files(provider {
    project(":lingua-core").tasks.named<Jar>("desktopNativesJar").get().archiveFile.get().asFile
}).builtBy(":lingua-core:desktopNativesJar")
kotlin.sourceSets.getByName("desktopMain").dependencies {
    runtimeOnly(files(desktopNatives))
}

compose.desktop {
    application {
        mainClass = "com.zhangke.lingua.demo.MainKt"
    }
}
