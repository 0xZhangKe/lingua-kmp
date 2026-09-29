package com.zhangke.lingua

internal actual fun createPlatformDetector(options: DetectorOptions): PlatformDetector =
    throw UnsupportedOperationException("lingua-rs detection is not yet implemented on desktop")
