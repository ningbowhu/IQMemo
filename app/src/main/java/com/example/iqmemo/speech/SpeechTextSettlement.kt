package com.example.iqmemo.speech

/** The manager snapshot includes partial text on timeout and may legitimately be empty. */
object SpeechTextSettlement {
    fun combine(confirmed: String, finalSession: String): String =
        listOf(confirmed, finalSession).filter { it.isNotBlank() }.joinToString("，")
}
