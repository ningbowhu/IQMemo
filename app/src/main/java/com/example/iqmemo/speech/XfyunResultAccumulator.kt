package com.example.iqmemo.speech

import org.json.JSONObject
import java.util.TreeMap

data class XfyunResultSlice(
    val sequence: Int?,
    val mode: String?,
    val replaceStart: Int?,
    val replaceEnd: Int?,
    val text: String
)

object XfyunResultSliceParser {
    private fun Char.isAsciiWord() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    fun parse(result: JSONObject): XfyunResultSlice {
        val text = buildString {
            val words = result.optJSONArray("ws") ?: return@buildString
            for (i in 0 until words.length()) {
                val candidates = words.optJSONObject(i)?.optJSONArray("cw") ?: continue
                // cw entries are alternative hypotheses, not consecutive words.
                val word = candidates.optJSONObject(0)?.optString("w").orEmpty()
                if (isNotEmpty() && word.isNotEmpty() &&
                    last().isAsciiWord() && word.first().isAsciiWord()) append(' ')
                append(word)
            }
        }
        val range = result.optJSONArray("rg")
        return XfyunResultSlice(
            sequence = if (result.has("sn")) result.optInt("sn") else null,
            mode = result.optString("pgs").ifBlank { null },
            replaceStart = range?.takeIf { it.length() >= 2 }?.optInt(0),
            replaceEnd = range?.takeIf { it.length() >= 2 }?.optInt(1),
            text = text
        )
    }
}

/**
 * 按讯飞 wpgs 协议合并流式识别结果。
 *
 * `apd` 结果按 `sn` 追加；`rpl` 先删除 `rg` 指定的历史返回序号范围，
 * 再把当前修正结果放到当前 `sn`。最终文本始终按序号排序拼接。
 */
class XfyunResultAccumulator {
    private val segments = TreeMap<Int, String>()
    private var fallbackSequence = 0
    private val seenSequences = mutableSetOf<Int>()
    private val replacedRanges = mutableListOf<IntRange>()

    fun apply(
        sequence: Int?,
        mode: String?,
        replaceStart: Int?,
        replaceEnd: Int?,
        text: String
    ): String {
        val resolvedSequence = sequence?.takeIf { it >= 0 } ?: ++fallbackSequence
        fallbackSequence = maxOf(fallbackSequence, resolvedSequence)

        if (replacedRanges.any { resolvedSequence in it }) return snapshot()
        if (!seenSequences.add(resolvedSequence)) return snapshot()

        if (mode == "rpl" && replaceStart != null && replaceEnd != null) {
            val start = minOf(replaceStart, replaceEnd)
            val end = maxOf(replaceStart, replaceEnd)
            segments.keys.removeAll { it in start..end }
            replacedRanges.add(start..end)
        }

        if (text.isNotEmpty()) {
            segments[resolvedSequence] = text
        } else {
            segments.remove(resolvedSequence)
        }
        return snapshot()
    }

    fun apply(slice: XfyunResultSlice): String = apply(
        sequence = slice.sequence,
        mode = slice.mode,
        replaceStart = slice.replaceStart,
        replaceEnd = slice.replaceEnd,
        text = slice.text
    )

    fun snapshot(): String = segments.values.joinToString(separator = "")

    fun clear() {
        segments.clear()
        fallbackSequence = 0
        seenSequences.clear()
        replacedRanges.clear()
    }
}
