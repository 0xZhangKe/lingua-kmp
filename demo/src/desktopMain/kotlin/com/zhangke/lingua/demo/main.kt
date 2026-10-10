package com.zhangke.lingua.demo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lingua API Tests",
        state = rememberWindowState(width = 900.dp, height = 900.dp),
    ) { App() }
}
