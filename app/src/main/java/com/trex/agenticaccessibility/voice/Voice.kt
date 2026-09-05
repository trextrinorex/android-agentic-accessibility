package com.trex.agenticaccessibility.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidSpeechToText(private val context: Context) {
    fun listen(onResult:(String)->Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        val recognizer=SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object: android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle?) { val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if(text!=null)onResult(text); recognizer.destroy() }
            override fun onError(error:Int){recognizer.destroy()}; override fun onReadyForSpeech(p:android.os.Bundle?){ }; override fun onBeginningOfSpeech(){}; override fun onRmsChanged(r:Float){}; override fun onBufferReceived(b:ByteArray?){ }; override fun onEndOfSpeech(){}; override fun onPartialResults(r:android.os.Bundle?){ }; override fun onEvent(t:Int,p:android.os.Bundle?){ }
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false) })
    }
}

class AndroidTextToSpeech(context: Context) {
    private var tts:TextToSpeech?=null
    init { tts=TextToSpeech(context){ if(it==TextToSpeech.SUCCESS)tts?.language=Locale.US } }
    fun speak(text:String){tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"agent")}
    fun stop(){tts?.stop()}
}
