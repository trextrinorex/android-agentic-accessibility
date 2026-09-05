package com.trex.agenticaccessibility.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidSpeechToText(private val context: Context) {
    @Volatile private var recognizer: SpeechRecognizer? = null

    fun listen(onResult: (String) -> Unit, onError: ((Int) -> Unit)? = null): Boolean {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke(SpeechRecognizer.ERROR_NOT_AVAILABLE)
            return false
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) onResult(text)
                cleanup(r)
            }
            override fun onError(error: Int) { onError?.invoke(error); cleanup(r) }
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
        return true
    }

    fun cancel() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }

    private fun cleanup(r: SpeechRecognizer) {
        if (recognizer === r) recognizer = null
        runCatching { r.destroy() }
    }
}

class AndroidTextToSpeech(context: Context) {
    private var tts: TextToSpeech? = null
    init { tts = TextToSpeech(context) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.US } }
    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent") }
    fun stop() { tts?.stop() }
}
