@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.zhangke.lingua

import cnames.structs.LinguaResult
import com.zhangke.lingua.cinterop.*
import kotlinx.cinterop.*

// UTF-16 spans retain embedded NULs and let Rust validate unpaired surrogates.
internal fun MemScope.nativeText(text: String): CValue<LinguaText> {
    val units = if (text.isEmpty()) null else allocArray<UShortVar>(text.length).also { buffer ->
        text.forEachIndexed { index, char -> buffer[index] = char.code.toUShort() }
    }
    return cValue {
        data = units
        len = text.length.toULong()
    }
}

internal fun MemScope.nativeTexts(texts: List<String>): CPointer<LinguaText>? =
    if (texts.isEmpty()) null else allocArray<LinguaText>(texts.size).also { array ->
        texts.forEachIndexed { index, text -> nativeText(text).place(array[index].ptr) }
    }

internal fun MemScope.nativeIds(languages: List<Language>): CPointer<IntVar>? =
    if (languages.isEmpty()) null else allocArray<IntVar>(languages.size).also { array ->
        languages.forEachIndexed { index, language -> array[index] = language.nativeId }
    }

// Copy results before releasing their Rust allocation, including on decoding errors.
internal fun <T> consumeNativeResult(result: CPointer<LinguaResult>?, decode: (CPointer<LinguaResult>) -> T): T {
    checkNotNull(result) { "Native result allocation failed" }
    try {
        val status = lingua_result_status(result)
        if (status != 0) {
            val message = lingua_result_message(result)?.toKString() ?: "Native operation failed"
            when (status) {
                1 -> throw IllegalArgumentException(message)
                else -> throw IllegalStateException(message)
            }
        }
        return decode(result)
    } finally {
        lingua_result_free(result)
    }
}

internal fun nativeUnit(result: CPointer<LinguaResult>?) {
    consumeNativeResult(result) { }
}

internal fun nativeRows(result: CPointer<LinguaResult>?): List<List<Double>> = consumeNativeResult(result) { pointer ->
    val rows = lingua_result_rows(pointer)
    check(rows <= Int.MAX_VALUE.toULong()) { "Native result too large" }
    List(rows.toInt()) { row ->
        val length = lingua_result_row_len(pointer, row.toULong())
        check(length <= Int.MAX_VALUE.toULong()) { "Native row too large" }
        val data = lingua_result_row_data(pointer, row.toULong())
        check(length == 0uL || data != null) { "Missing native row data" }
        List(length.toInt()) { index -> checkNotNull(data)[index] }
    }
}

internal fun nativeRow(result: CPointer<LinguaResult>?): List<Double> = nativeRows(result).single()
