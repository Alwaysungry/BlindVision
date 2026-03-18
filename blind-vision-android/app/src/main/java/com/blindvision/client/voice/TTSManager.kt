package com.blindvision.client.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSManager(context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    
    private var onStartCallback: (() -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.CHINESE
                setupListener()
            }
        }
    }
    
    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onStartCallback?.invoke()
            }
            
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onCompleteCallback?.invoke()
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onErrorCallback?.invoke("TTS error")
            }
        })
    }
    
    fun setCallbacks(onStart: (() -> Unit)? = null, onComplete: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        onStartCallback = onStart
        onCompleteCallback = onComplete
        onErrorCallback = onError
    }
    
    fun speak(text: String, interrupt: Boolean = false): Boolean {
        if (!isInitialized || tts == null) return false
        
        if (isSpeaking && !interrupt) return false
        
        if (isSpeaking && interrupt) {
            stop()
        }
        
        val utteranceId = System.currentTimeMillis().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return true
    }
    
    fun speakHighPriority(text: String) {
        speak(text, interrupt = true)
    }
    
    fun stop() {
        tts?.stop()
        isSpeaking = false
    }
    
    fun isEngineSpeaking(): Boolean = isSpeaking
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
