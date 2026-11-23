package com.example.iqmemo.speech

/**
 * Contract for one-sentence voice capture.
 *
 * The speech service finalizes a sentence after this amount of trailing silence.
 * Empty final results keep the recorder open so merely waiting before speaking does
 * not create or close an empty note.
 */
object SpeechAutoFinishPolicy {
    const val END_OF_SPEECH_SILENCE_MS = 2_500

    fun shouldFinish(finalText: String): Boolean = finalText.isNotBlank()
}
