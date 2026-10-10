import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.publish) apply false
}

allprojects {
    group = "io.github.0xzhangke"
    version = "0.0.1"

    plugins.withId("com.vanniktech.maven.publish.base") {
        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral(automaticRelease = true)
            val hasInMemorySigningKey = providers.gradleProperty("signingInMemoryKey").isPresent
            val hasFileSigningKey = providers.gradleProperty("signing.keyId").isPresent &&
                providers.gradleProperty("signing.password").isPresent &&
                providers.gradleProperty("signing.secretKeyRingFile").isPresent
            val hasGpgSigningKey = providers.gradleProperty("signing.gnupg.keyName").isPresent
            // Compiling and testing must work without publication credentials.
            // Keep signing mandatory whenever a publication/signing task is executed.
            gradle.taskGraph.whenReady {
                if (allTasks.any { it.project == project &&
                        (it.name.startsWith("publish") || it.name.startsWith("sign")) }) {
                    check(hasInMemorySigningKey || hasFileSigningKey || hasGpgSigningKey) {
                        "Publishing requires signing. Configure signingInMemoryKey, signing.keyId/password/secretKeyRingFile, " +
                            "or signing.gnupg.keyName before running publish tasks."
                    }
                }
            }
            signAllPublications()
            if (hasGpgSigningKey) {
                extensions.configure<SigningExtension>("signing") {
                    useGpgCmd()
                }
            }
            pom {
                name.set("lingua-kmp")
                description.set("Kotlin Multiplatform library for lingua-rs")
                url.set("https://github.com/0xZhangKe/lingua-kmp")
                licenses {
                    license {
                        name.set("Apache 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("zhangke")
                        name.set("Zhangke")
                        url.set("https://github.com/0xZhangKe")
                    }
                }
                scm {
                    url.set("https://github.com/0xZhangKe/lingua-kmp")
                    connection.set("scm:git:git://github.com:0xZhangKe/lingua-kmp.git")
                    developerConnection.set("scm:git:git://github.com:0xZhangKe/lingua-kmp.git")
                }
            }
        }
    }
}
