package com.zhangke.lingua.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.zhangke.lingua.Language
import com.zhangke.lingua.demo.ui.theme.LinguakmpTheme
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App() {
    LinguakmpTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            DetectionScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun DetectionScreen(modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("😀 上海大学是一个好大学. It is such a great university.") }
    var batchInput by rememberSaveable {
        mutableStateOf("Hello, how are you today?\nBonjour, comment allez-vous aujourd’hui ?\n今日はいい天気ですね\n这是一个用于测试语言识别的中文句子。")
    }
    var languageName by rememberSaveable { mutableStateOf(Language.ENGLISH.name) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<DetectorExample?>(null) }
    val results = remember { mutableStateMapOf<DetectorExample, String>() }
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LanguageDetector API Tests", style = MaterialTheme.typography.headlineSmall)
        Text("Each example runs in the background with a separate detector configured for all languages. Use empty text to test unrecognized input.")
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Single / mixed-language text") },
            enabled = running == null,
            minLines = 3,
        )
        OutlinedTextField(
            value = batchInput,
            onValueChange = { batchInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Batch text (one entry per line, including empty lines)") },
            enabled = running == null,
            minLines = 4,
        )
        Box {
            OutlinedButton(
                enabled = running == null,
                onClick = { languageMenuExpanded = true },
            ) { Text("Confidence target language: $languageName") }
            DropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                Language.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text("${language.name} (${language.isoCode6391.code})") },
                        onClick = {
                            languageName = language.name
                            languageMenuExpanded = false
                        },
                    )
                }
            }
        }
        DetectorExample.entries.forEach { example ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(example.method, style = MaterialTheme.typography.titleMedium)
                    Text(example.description)
                    Button(
                        enabled = running == null && !isPreview,
                        onClick = {
                            val text = input
                            val texts = batchInput.split('\n')
                            val language = Language.valueOf(languageName)
                            running = example
                            results.remove(example)
                            scope.launch {
                                try {
                                    val measured = withContext(Dispatchers.Default) {
                                        measureTimedValue { example.run(text, texts, language) }
                                    }
                                    results[example] = "${measured.value}\nElapsed: ${measured.duration.toDouble(DurationUnit.MILLISECONDS).formatted(2)} ms (including detector creation and cleanup)"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    results[example] = "Failed: ${error::class.simpleName}: ${error.message}"
                                } catch (error: Error) {
                                    results[example] = "Native library loading or invocation failed: ${error.message}"
                                } finally {
                                    running = null
                                }
                            }
                        },
                    ) { Text(if (running == example) "Running…" else "Run example") }
                    results[example]?.let { result ->
                        SelectionContainer { Text(result) }
                    }
                }
            }
        }
    }
}

