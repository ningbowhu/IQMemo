package com.example.iqmemo.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class XfyunResultAccumulatorTest {
    @Test
    fun `apd results are assembled by sequence number`() {
        val accumulator = XfyunResultAccumulator()

        accumulator.apply(1, "apd", null, null, "今天")
        accumulator.apply(3, "apd", null, null, "开会")
        val result = accumulator.apply(2, "apd", null, null, "下午")

        assertEquals("今天下午开会", result)
    }

    @Test
    fun `rpl replaces only the declared history range`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "今天")
        accumulator.apply(2, "apd", null, null, "下雨")
        accumulator.apply(3, "apd", null, null, "很大")

        val result = accumulator.apply(4, "rpl", 2, 3, "下午开会")

        assertEquals("今天下午开会", result)
    }

    @Test
    fun `multiple corrections preserve text outside each replacement range`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "提醒我")
        accumulator.apply(2, "apd", null, null, "明天")
        accumulator.apply(3, "rpl", 2, 2, "后天")
        accumulator.apply(4, "apd", null, null, "交报告")

        val result = accumulator.apply(5, "rpl", 3, 4, "下午交报告。")

        assertEquals("提醒我下午交报告。", result)
    }

    @Test
    fun `legacy response without sequence remains additive`() {
        val accumulator = XfyunResultAccumulator()

        accumulator.apply(null, null, null, null, "第一段")
        val result = accumulator.apply(null, null, null, null, "第二段")

        assertEquals("第一段第二段", result)
    }

    @Test
    fun `clear starts an independent websocket session`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "旧会话")

        accumulator.clear()

        assertEquals("", accumulator.snapshot())
        assertEquals("新会话", accumulator.apply(1, "apd", null, null, "新会话"))
    }
    @Test
    fun `late original result cannot revive words replaced by a correction`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "明天")
        accumulator.apply(3, "rpl", 2, 2, "开会")
        assertEquals("明天开会", accumulator.apply(2, "apd", null, null, "开惠"))
    }

    @Test
    fun `duplicate correction cannot erase later corrected text`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "预算")
        accumulator.apply(2, "rpl", 1, 1, "雨伞")
        accumulator.apply(3, "rpl", 2, 2, "预算。")
        assertEquals("预算。", accumulator.apply(2, "rpl", 1, 1, "雨伞"))
    }

    @Test
    fun `empty correction removes rejected words without restoring partial`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "杂音")
        assertEquals("", accumulator.apply(2, "rpl", 1, 1, ""))
        assertEquals("", accumulator.apply(1, "apd", null, null, "杂音"))
    }
    @Test
    fun `duplicate empty correction cannot revive rejected text`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(1, "apd", null, null, "杂音")
        accumulator.apply(2, "rpl", 1, 1, "")
        assertEquals("", accumulator.apply(2, "rpl", 1, 1, "杂音"))
    }
}
