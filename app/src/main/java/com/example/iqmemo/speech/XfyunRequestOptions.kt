package com.example.iqmemo.speech

import org.json.JSONObject

/** Neutral Mandarin dictation; keep server corrections enabled instead of guessing words locally. */
object XfyunRequestOptions {
    fun business(language: SpeechLanguage = SpeechLanguage.CHINESE): JSONObject = JSONObject().apply {
        put("language", language.value)
        put("domain", "iat")
        put("accent", "mandarin")
        put("vad_eos", SpeechAutoFinishPolicy.END_OF_SPEECH_SILENCE_MS)
        if (language == SpeechLanguage.CHINESE) {
            put("dwa", "wpgs")
            put("ptt", 1)
            put("rlang", "zh-cn")
            put("nunum", 1)
            put("ltc", 1) // keep Chinese and English; never filter out English words
        }
        put("nbest", 1)
        put("wbest", 1)
    }
}
