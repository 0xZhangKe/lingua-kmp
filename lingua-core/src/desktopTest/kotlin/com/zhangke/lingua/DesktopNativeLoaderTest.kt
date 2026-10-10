package com.zhangke.lingua

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.*

class DesktopNativeLoaderTest {
    @Test
    fun platformAliasesLocateArchitectureSpecificResources() {
        assertEquals("/natives/macos-aarch64/liblingua_kmp.dylib", DesktopPlatform.resolve("Mac OS X", "aarch64").resourcePath)
        assertEquals(DesktopPlatform.resolve("Mac OS X", "aarch64"), DesktopPlatform.resolve("Darwin", "arm64"))
        assertEquals("/natives/windows-x86_64/lingua_kmp.dll", DesktopPlatform.resolve("Windows 11", "amd64").resourcePath)
        assertEquals("/natives/linux-x86_64/liblingua_kmp.so", DesktopPlatform.resolve("Linux", "x86_64").resourcePath)
        assertFailsWith<UnsupportedOperationException> { DesktopPlatform.resolve("FreeBSD", "aarch64") }
        assertFailsWith<UnsupportedOperationException> { DesktopPlatform.resolve("Mac OS X", "x86") }
    }
}

// Runs only in desktopPackagedTest, whose classpath uses the two built JARs.
class DesktopPackagedTest {
    @Test
    fun publishedJarResourcesLoadWithoutLibraryPathConfiguration() {
        assertTrue(LanguageDetector::class.java.protectionDomain.codeSource.location.path.endsWith(".jar"))
        val platform = DesktopPlatform.resolve(System.getProperty("os.name"), System.getProperty("os.arch"))
        val resource = assertNotNull(LanguageDetector::class.java.getResource(platform.resourcePath))
        assertEquals("jar", resource.protocol)
        LanguageDetectorBuilder.fromLanguages(Language.JAPANESE, Language.CHINESE).build().use {
            assertEquals(Language.JAPANESE, it.detectLanguageOf("今日はいい天気ですね"))
        }
    }

    @Test
    fun missingNativeJarReportsTheRequiredClassifier() {
        // A separate JVM avoids LinguaNative's initialization in other tests.
        val classpath = listOf(LanguageDetector::class.java, Unit::class.java, DesktopNativeLoaderProbe::class.java)
            .map { Paths.get(it.protectionDomain.codeSource.location.toURI()).toString() }
            .distinct().joinToString(File.pathSeparator)
        val executable = File(System.getProperty("java.home"), "bin/java${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}")
        val child = ProcessBuilder(executable.path, "-cp", classpath, DesktopNativeLoaderProbe::class.java.name)
            .redirectErrorStream(true).start()
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Native loader probe timed out")
            val output = child.inputStream.bufferedReader().use { it.readText() }
            assertEquals(2, child.exitValue(), output)
            val platform = DesktopPlatform.resolve(System.getProperty("os.name"), System.getProperty("os.arch"))
            assertTrue(output.contains(platform.resourcePath), output)
            assertTrue(output.contains("natives-${platform.id}"), output)
        } finally {
            if (child.isAlive) child.destroyForcibly()
        }
    }

}


internal object DesktopNativeLoaderProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            LanguageDetectorBuilder.fromLanguages(Language.JAPANESE).build().use {
                check(it.detectLanguageOf("今日はいい天気ですね") == Language.JAPANESE)
            }
        } catch (error: UnsatisfiedLinkError) {
            System.err.println(error.message)
            exitProcess(2)
        }
    }
}
