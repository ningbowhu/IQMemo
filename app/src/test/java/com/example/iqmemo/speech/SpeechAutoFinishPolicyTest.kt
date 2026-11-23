package com.example.iqmemo.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAutoFinishPolicyTest {
    @Test
    fun `sentence endpoint uses a two point five seconds for natural pauses`() {
        assertEquals(2_500, SpeechAutoFinishPolicy.END_OF_SPEECH_SILENCE_MS)
    }

    @Test
    fun `non-empty final text completes the voice note`() {
        assertTrue(SpeechAutoFinishPolicy.shouldFinish("提醒我下午开会"))
    }

    @Test
    fun `empty endpoint does not close an untouched recorder`() {
        assertFalse(SpeechAutoFinishPolicy.shouldFinish("  "))
    }
}
