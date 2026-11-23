package com.example.iqmemo.speech

enum class SpeechLanguage(val value: String) {
    CHINESE("zh_cn"), ENGLISH("en_us");
    companion object {
        fun from(value: String?): SpeechLanguage = entries.firstOrNull { it.value == value } ?: CHINESE
    }
}
