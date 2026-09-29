package com.zhangke.lingua.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhangke.lingua.LinguaNative
import com.zhangke.lingua.demo.ui.theme.LinguakmpTheme
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinguakmpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DetectionScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionScreen(modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("Hello, this is a language detection test.") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsedMillis by rememberSaveable { mutableStateOf<Double?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("待检测文本") },
            enabled = !isDetecting,
            minLines = 3,
        )
        Button(
            enabled = !isDetecting,
            onClick = {
                val text = input
                isDetecting = true
                result = null
                elapsedMillis = null
                scope.launch {
                    try {
                        val detection = withContext(Dispatchers.Default) {
                            measureTimedValue { LinguaNative.detect(text) }
                        }
                        result = detection.value.toString()
                        elapsedMillis = detection.duration.toDouble(DurationUnit.MILLISECONDS)
                    } catch (error: LinkageError) {
                        result = "Native 库加载或调用失败：${error.message}"
                    } finally {
                        isDetecting = false
                    }
                }
            },
        ) {
            Text(if (isDetecting) "检测中…" else "检测")
        }
        result?.let { Text(text = it) }
        elapsedMillis?.let {
            Text(text = "检测耗时：${String.format(Locale.ROOT, "%.2f", it)} ms")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectionScreenPreview() {
    LinguakmpTheme {
        DetectionScreen()
    }
}
