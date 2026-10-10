package com.zhangke.lingua.demo

import com.zhangke.lingua.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectorExamplesTest {
    @Test
    fun everyExampleRunsThroughTheSharedRunner() {
        val text = "今日はいい天気ですね"
        for (example in DetectorExample.entries) {
            val result = example.run(text, listOf(text, ""), Language.JAPANESE)
            assertTrue(result.isNotBlank(), "${example.method} returned no result")
            if (example == DetectorExample.CLOSE) {
                assertEquals(9, result.lines().count { it.endsWith("passed (IllegalStateException)") })
            }
        }
    }

    @Test
    fun confidencesAndElapsedTimeKeepTheirDecimalPrecision() {
        assertEquals("0.000000", 0.0.formatted())
        assertEquals("1.000000", 1.0.formatted())
        assertEquals("1.000000", 0.9999999.formatted())
        assertEquals("0.123457", 0.1234567.formatted())
        assertEquals("12.35", 12.3456.formatted(2))
        assertEquals("0.00", 0.0.formatted(2))
    }
}
