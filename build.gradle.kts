import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.jetbrains.compose.compiler) apply false
    alias(libs.plugins.publish) apply false
}

allprojects {
    group = "io.github.0xzhangke"
    version = "0.2.6"

    plugins.withId("com.vanniktech.maven.publish.base") {
        extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
            val hasInMemorySigningKey = providers.gradleProperty("signingInMemoryKey").isPresent
            val hasFileSigningKey = providers.gradleProperty("signing.keyId").isPresent &&
                providers.gradleProperty("signing.password").isPresent &&
                providers.gradleProperty("signing.secretKeyRingFile").isPresent
            val hasGpgSigningKey = providers.gradleProperty("signing.gnupg.keyName").isPresent
            check(hasInMemorySigningKey || hasFileSigningKey || hasGpgSigningKey) {
                "Publishing requires signing. Configure signingInMemoryKey, signing.keyId/password/secretKeyRingFile, " +
                    "or signing.gnupg.keyName before running publish tasks."
            }
            signAllPublications()
            if (hasGpgSigningKey) {
                extensions.configure<SigningExtension>("signing") {
                    useGpgCmd()
                }
            }
            pom {
                name.set("Compose Agent Render")
                description.set("Compose Multiplatform renderer for agent output streams.")
                url.set("https://github.com/0xZhangKe/compose-agent-render")
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
                    url.set("https://github.com/0xZhangKe/compose-agent-render")
                    connection.set("scm:git:git://github.com:0xZhangKe/compose-agent-render.git")
                    developerConnection.set("scm:git:git://github.com:0xZhangKe/compose-agent-render.git")
                }
            }
        }
    }
}
