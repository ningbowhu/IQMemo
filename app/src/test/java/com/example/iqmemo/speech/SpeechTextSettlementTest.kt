package com.example.iqmemo.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextSettlementTest {
    @Test fun finalCorrectionReplacesPartialWithoutDuplicatingIt() {
        val session = XfyunResultAccumulator()
        session.apply(1, "apd", null, null, "明天下午开惠")
        session.apply(2, "rpl", 1, 1, "明天下午开会。")
        assertEquals("明天下午开会。", SpeechTextSettlement.combine("", session.snapshot()))
    }
    @Test fun timeoutUsesLatestKnownSessionSnapshot() {
        val session = XfyunResultAccumulator()
        session.apply(1, "apd", null, null, "预算十二万元")
        assertEquals("上一段，预算十二万元", SpeechTextSettlement.combine("上一段", session.snapshot()))
    }
    @Test fun reconnectPrefixRemainsWhenTailIsCorrected() {
        assertEquals("项目评审，周五提交", SpeechTextSettlement.combine("项目评审", "周五提交"))
    }
    @Test fun emptyCorrectionDoesNotResurrectRejectedPartial() {
        val session = XfyunResultAccumulator()
        session.apply(1, "apd", null, null, "杂音")
        session.apply(2, "rpl", 1, 1, "")
        assertEquals("", SpeechTextSettlement.combine("", session.snapshot()))
    }
}
