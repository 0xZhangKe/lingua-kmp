package com.zhangke.lingua

internal actual fun loadNativeLibrary() {
    System.loadLibrary("lingua_kmp")
}

internal actual fun reportNativeCleanupFailure(error: Exception) {
    android.util.Log.e("Lingua", "Native detector cleanup failed", error)
}
