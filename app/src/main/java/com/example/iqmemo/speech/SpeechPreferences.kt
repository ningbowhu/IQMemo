package com.example.iqmemo.speech

import android.content.Context

object SpeechPreferences {
    private const val FILE = "speech_preferences"
    fun language(context: Context): SpeechLanguage = SpeechLanguage.from(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("language", null)
    )
    fun setLanguage(context: Context, language: SpeechLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("language", language.value).apply()
    }
}
