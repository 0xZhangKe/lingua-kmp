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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateMapOf
import com.zhangke.lingua.DetectionResult
import com.zhangke.lingua.Language
import com.zhangke.lingua.LanguageConfidence
import com.zhangke.lingua.LanguageDetectorBuilder
import kotlinx.coroutines.CancellationException
import com.zhangke.lingua.demo.ui.theme.LinguakmpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinguakmpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DetectionScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
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
                                    results[example] = "${measured.value}\nElapsed: ${String.format(Locale.ROOT, "%.2f", measured.duration.toDouble(DurationUnit.MILLISECONDS))} ms (including detector creation and cleanup)"
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    results[example] = "Failed: ${error.javaClass.simpleName}: ${error.message}"
                                } catch (error: LinkageError) {
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

private enum class DetectorExample(val method: String, val description: String) {
    DETECT("detectLanguageOf", "Detect the language of a single text; returns null when detection is unreliable."),
    DETECT_PARALLEL("detectLanguagesInParallelOf", "Detect each line in parallel and display results in input order."),
    CONFIDENCE("computeLanguageConfidence", "Compute the confidence for the target language in a single text."),
    CONFIDENCE_VALUES("computeLanguageConfidenceValues", "Display confidence values for all configured languages in descending order."),
    CONFIDENCE_PARALLEL("computeLanguageConfidenceInParallel", "Compute the confidence for the target language for each line in parallel."),
    CONFIDENCE_VALUES_PARALLEL("computeLanguageConfidenceValuesInParallel", "Compute confidence values for all languages for each line in parallel."),
    MULTIPLE("detectMultipleLanguagesOf", "Detect language segments in mixed text and display UTF-16 offsets, word counts, and original text."),
    MULTIPLE_PARALLEL("detectMultipleLanguagesInParallelOf", "Detect language segments for each line in parallel."),
    UNLOAD("unloadLanguageModels", "Detect → unload shared models → detect again to verify models can be reloaded."),
    CLOSE("close", "Close twice to verify idempotency and that all other 9 methods reject calls after closing.");

    fun run(text: String, texts: List<String>, language: Language): String =
        LanguageDetectorBuilder.fromAllLanguages().build().use { detector ->
            when (this) {
                DETECT -> detector.detectLanguageOf(text).displayName()
                DETECT_PARALLEL -> detector.detectLanguagesInParallelOf(texts)
                    .mapIndexed { index, value -> "${index + 1}. ${texts[index]} → ${value.displayName()}" }.joinToString("\n")
                CONFIDENCE -> "$language: ${detector.computeLanguageConfidence(text, language).formatted()}"
                CONFIDENCE_VALUES -> detector.computeLanguageConfidenceValues(text).formatConfidences()
                CONFIDENCE_PARALLEL -> detector.computeLanguageConfidenceInParallel(texts, language)
                    .mapIndexed { index, value -> "${index + 1}. ${texts[index]} → $language: ${value.formatted()}" }.joinToString("\n")
                CONFIDENCE_VALUES_PARALLEL -> detector.computeLanguageConfidenceValuesInParallel(texts)
                    .mapIndexed { index, values -> "${index + 1}. ${texts[index]}\n${values.formatConfidences()}" }.joinToString("\n\n")
                MULTIPLE -> detector.detectMultipleLanguagesOf(text).formatSegments(text)
                MULTIPLE_PARALLEL -> detector.detectMultipleLanguagesInParallelOf(texts)
                    .mapIndexed { index, values -> "${index + 1}. ${texts[index]}\n${values.formatSegments(texts[index])}" }.joinToString("\n\n")
                UNLOAD -> {
                    val before = detector.detectLanguageOf(text)
                    detector.unloadLanguageModels()
                    val after = detector.detectLanguageOf(text)
                    check(before == after) { "Detection results differ before and after unloading" }
                    "Passed: models unloaded and detection succeeded again.\nBefore unloading: ${before.displayName()}\nAfter unloading: ${after.displayName()}"
                }
                CLOSE -> {
                    detector.close()
                    detector.close()
                    val calls: List<Pair<String, () -> Any?>> = listOf(
                        DETECT.method to { detector.detectLanguageOf(text) },
                        DETECT_PARALLEL.method to { detector.detectLanguagesInParallelOf(texts) },
                        CONFIDENCE.method to { detector.computeLanguageConfidence(text, language) },
                        CONFIDENCE_VALUES.method to { detector.computeLanguageConfidenceValues(text) },
                        CONFIDENCE_PARALLEL.method to { detector.computeLanguageConfidenceInParallel(texts, language) },
                        CONFIDENCE_VALUES_PARALLEL.method to { detector.computeLanguageConfidenceValuesInParallel(texts) },
                        MULTIPLE.method to { detector.detectMultipleLanguagesOf(text) },
                        MULTIPLE_PARALLEL.method to { detector.detectMultipleLanguagesInParallelOf(texts) },
                        UNLOAD.method to { detector.unloadLanguageModels() },
                    )
                    "Passed: repeated close() succeeded.\n" + calls.joinToString("\n") { (name, call) ->
                        val error = runCatching { call() }.exceptionOrNull()
                        check(error is IllegalStateException) { "$name did not throw the expected IllegalStateException: $error" }
                        "$name: passed (IllegalStateException)"
                    }
                }
            }
        }
}

private fun Language?.displayName(): String = this?.let { "${it.name} (${it.isoCode6391.code})" } ?: "null (unreliable detection)"

private fun Double.formatted(): String = String.format(Locale.ROOT, "%.6f", this)

private fun List<LanguageConfidence>.formatConfidences(): String =
    if (isEmpty()) "[]" else joinToString("\n") { "${it.language.displayName()}: ${it.value.formatted()}" }

private fun List<DetectionResult>.formatSegments(text: String): String =
    if (isEmpty()) "[] (no language segments)" else joinToString("\n") {
        "${it.language.displayName()} [${it.startIndex}, ${it.endIndex}), word count: ${it.wordCount}\n${text.substring(it.startIndex, it.endIndex)}"
    }

@Preview(showBackground = true)
@Composable
private fun DetectionScreenPreview() {
    LinguakmpTheme {
        DetectionScreen()
    }
}
