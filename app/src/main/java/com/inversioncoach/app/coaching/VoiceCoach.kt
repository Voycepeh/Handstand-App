package com.inversioncoach.app.coaching

import android.content.Context
import android.speech.tts.TextToSpeech
import android.os.Bundle
import com.inversioncoach.app.model.CoachingCue
import java.util.Locale

class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
        }
    }

    fun speak(cue: CoachingCue, volume: Float = 1f) {
        if (!ready) return

        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
        }
        tts.speak(cue.text, TextToSpeech.QUEUE_FLUSH, params, "${cue.id}_${cue.generatedAtMs}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
