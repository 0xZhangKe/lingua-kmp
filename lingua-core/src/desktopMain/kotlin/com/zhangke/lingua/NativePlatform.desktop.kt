package com.zhangke.lingua

import java.nio.file.Files
import java.util.Locale

internal actual fun loadNativeLibrary(): Unit = DesktopNativeLoader.load()

internal actual fun reportNativeCleanupFailure(error: Exception) {
    System.err.println("Lingua: native detector cleanup failed: $error")
}

internal data class DesktopPlatform(val os: String, val arch: String) {
    val id: String get() = "$os-$arch"
    val libraryName: String get() = when (os) {
        "macos" -> "liblingua_kmp.dylib"
        "linux" -> "liblingua_kmp.so"
        "windows" -> "lingua_kmp.dll"
        else -> error("Unsupported desktop operating system: $os")
    }
    val resourcePath: String get() = "/natives/$id/$libraryName"

    companion object {
        fun resolve(osName: String, archName: String): DesktopPlatform {
            val os = when {
                osName.lowercase(Locale.ROOT).startsWith("windows") -> "windows"
                osName.lowercase(Locale.ROOT).startsWith("mac") || osName.equals("Darwin", ignoreCase = true) -> "macos"
                osName.equals("Linux", ignoreCase = true) -> "linux"
                else -> throw UnsupportedOperationException("Unsupported desktop OS: $osName")
            }
            val arch = when (archName.lowercase(Locale.ROOT)) {
                "aarch64", "arm64" -> "aarch64"
                "amd64", "x86_64", "x64" -> "x86_64"
                else -> throw UnsupportedOperationException("Unsupported desktop architecture: $archName")
            }
            return DesktopPlatform(os, arch)
        }
    }
}

private object DesktopNativeLoader {
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        val platform = DesktopPlatform.resolve(System.getProperty("os.name"), System.getProperty("os.arch"))
        val input = DesktopNativeLoader::class.java.getResourceAsStream(platform.resourcePath)
            ?: throw UnsatisfiedLinkError(
                "Missing Lingua native library ${platform.resourcePath}. " +
                    "Add io.github.0xzhangke:lingua-core-desktop:<version>:natives-${platform.id} to your runtime dependencies.",
            )
        input.use { stream ->
            // Unique directories also avoid native-library collisions between class loaders.
            val directory = Files.createTempDirectory("lingua-${platform.id}-")
            val library = directory.resolve(platform.libraryName)
            // Windows keeps loaded DLLs locked. Delete-on-exit reverses this order.
            directory.toFile().deleteOnExit()
            library.toFile().deleteOnExit()
            try {
                Files.copy(stream, library)
                System.load(library.toAbsolutePath().toString())
                loaded = true
            } finally {
                // Unix permits unlinking a loaded library. Windows retries at process exit.
                if (!loaded || platform.os != "windows") {
                    // Cleanup must not obscure the original load failure or invalidate
                    // a successful load; deleteOnExit remains the fallback.
                    runCatching {
                        Files.deleteIfExists(library)
                        Files.deleteIfExists(directory)
                    }
                }
            }
        }
    }
}
