package com.blindvision.client.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceController(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceController"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    private var onResultCallback: ((String) -> Unit)? = null
    private var onStartCallback: (() -> Unit)? = null
    private var onEndCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        setupListener()
    }
    
    private fun setupListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onStartCallback?.invoke()
            }
            
            override fun onBeginningOfSpeech() {}
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                isListening = false
                onEndCallback?.invoke()
            }
            
            override fun onError(error: Int) {
                isListening = false
                val errorMsg = getErrorText(error)
                Log.e(TAG, "Recognition error: $errorMsg")
                onErrorCallback?.invoke(errorMsg)
            }
            
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResultCallback?.invoke(matches[0])
                }
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }
    
    fun setCallbacks(
        onResult: (String) -> Unit,
        onStart: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        onResultCallback = onResult
        onStartCallback = onStart
        onEndCallback = onEnd
        onErrorCallback = onError
    }
    
    fun startListening() {
        if (isListening) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }
    
    fun cancelListening() {
        speechRecognizer?.cancel()
        isListening = false
    }
    
    fun getListeningState(): Boolean = isListening
    
    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
    }
}
