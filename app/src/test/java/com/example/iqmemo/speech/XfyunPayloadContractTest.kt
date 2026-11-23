package com.example.iqmemo.speech

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class XfyunPayloadContractTest {
    @Test
    fun `official rpl payload shape corrects only rg sequence range`() {
        val accumulator = XfyunResultAccumulator()
        accumulator.apply(XfyunResultSliceParser.parse(payload(1, "apd", null, "我想")))
        accumulator.apply(XfyunResultSliceParser.parse(payload(2, "apd", null, "记住")))

        val corrected = accumulator.apply(
            XfyunResultSliceParser.parse(payload(3, "rpl", 1 to 2, "提醒我"))
        )

        assertEquals("提醒我", corrected)
    }

    @Test
    fun `parser joins all best candidate words in a slice`() {
        val payload = JSONObject(
            """
            {
              "sn": 7,
              "pgs": "apd",
              "ws": [
                {"cw": [{"w": "下午"}]},
                {"cw": [{"w": "3点"}]},
                {"cw": [{"w": "开会。"}]}
              ]
            }
            """.trimIndent()
        )

        val slice = XfyunResultSliceParser.parse(payload)

        assertEquals(7, slice.sequence)
        assertEquals("下午3点开会。", slice.text)
    }

    @Test
    fun `alternative candidates never concatenate into the note`() {
        val result = JSONObject("""{"sn":1,"ws":[{"cw":[{"w":"开会"},{"w":"开惠"}]},{"cw":[{"w":"。"}]}]}""")
        assertEquals("开会。", XfyunResultSliceParser.parse(result).text)
    }

    @Test
    fun `empty candidate list does not erase neighbouring words`() {
        val result = JSONObject("""{"sn":2,"ws":[{"cw":[]},{"cw":[{"w":"预算"}]}]}""")
        assertEquals("预算", XfyunResultSliceParser.parse(result).text)
    }

    @Test
    fun `neutral request retains dynamic corrections punctuation and full context`() {
        val business = XfyunRequestOptions.business()
        assertEquals("wpgs", business.getString("dwa"))
        assertEquals("mandarin", business.getString("accent"))
        assertEquals(1, business.getInt("ptt"))
        assertEquals(1, business.getInt("wbest"))
        assertEquals(false, business.has("pd"))
    }

    @Test
    fun `english uses english engine without chinese-only options`() {
        val business = XfyunRequestOptions.business(SpeechLanguage.ENGLISH)
        assertEquals("en_us", business.getString("language"))
        assertEquals(false, business.has("dwa"))
        assertEquals(false, business.has("rlang"))
        assertEquals(false, business.has("nunum"))
    }

    @Test
    fun `chinese mixed mode does not filter english words`() {
        assertEquals(1, XfyunRequestOptions.business().getInt("ltc"))
        assertEquals(SpeechLanguage.CHINESE, SpeechLanguage.from("obsolete-setting"))
    }

    @Test
    fun `english word boundaries retain readable spaces`() {
        val result = JSONObject("""{"sn":1,"ws":[{"cw":[{"w":"review"}]},{"cw":[{"w":"the"}]},{"cw":[{"w":"API"}]},{"cw":[{"w":"设计。"}]}]}""")
        assertEquals("review the API设计。", XfyunResultSliceParser.parse(result).text)
    }

    private fun payload(
        sequence: Int,
        mode: String,
        range: Pair<Int, Int>?,
        text: String
    ): JSONObject = JSONObject().apply {
        put("sn", sequence)
        put("pgs", mode)
        range?.let { put("rg", listOf(it.first, it.second)) }
        put("ws", listOf(mapOf("cw" to listOf(mapOf("w" to text)))))
    }
}
