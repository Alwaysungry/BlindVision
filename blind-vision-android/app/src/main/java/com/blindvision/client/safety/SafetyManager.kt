package com.blindvision.client.safety

import com.blindvision.client.data.model.RiskLevel
import com.blindvision.client.data.model.SafetyEvent
import com.blindvision.client.voice.TTSManager
import kotlinx.coroutines.*

class SafetyManager(private val ttsManager: TTSManager) {
    
    private var isInSafetyMode = false
    private var lastResponseTime = 0L
    private val safetyTimeoutMs = 8000L
    
    private var safetyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var onSafetyModeEnterCallback: (() -> Unit)? = null
    private var onSafetyModeExitCallback: (() -> Unit)? = null
    private var onSafetyEventCallback: ((SafetyEvent) -> Unit)? = null
    
    fun setCallbacks(
        onEnter: (() -> Unit)? = null,
        onExit: (() -> Unit)? = null,
        onEvent: ((SafetyEvent) -> Unit)? = null
    ) {
        onSafetyModeEnterCallback = onEnter
        onSafetyModeExitCallback = onExit
        onSafetyEventCallback = onEvent
    }
    
    fun startMonitoring() {
        lastResponseTime = System.currentTimeMillis()
        safetyJob = scope.launch {
            while (isActive) {
                delay(1000)
                checkSafetyConditions()
            }
        }
    }
    
    private fun checkSafetyConditions() {
        val timeSinceLastResponse = System.currentTimeMillis() - lastResponseTime
        if (timeSinceLastResponse > safetyTimeoutMs && !isInSafetyMode) {
            enterSafetyMode("timeout", "${timeSinceLastResponse}ms 无响应")
        }
    }
    
    fun recordResponse() {
        lastResponseTime = System.currentTimeMillis()
        if (isInSafetyMode) {
            exitSafetyMode()
        }
    }

    fun recordActivity() {
        lastResponseTime = System.currentTimeMillis()
    }
    
    fun handleNetworkDisconnect() {
        if (!isInSafetyMode) {
            enterSafetyMode("network_disconnect", "网络连接断开")
        }
    }
    
    fun handleAPIError(error: String) {
        if (!isInSafetyMode) {
            enterSafetyMode("api_error", "API 错误: $error")
        }
    }
    
    fun handleHighRiskMovement() {
        ttsManager.speakHighPriority("检测到风险，请暂停移动")
    }
    
    private fun enterSafetyMode(reason: String, message: String) {
        isInSafetyMode = true
        val event = SafetyEvent(
            type = reason,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        onSafetyModeEnterCallback?.invoke()
        onSafetyEventCallback?.invoke(event)
        ttsManager.speakHighPriority("网络异常，请暂停移动")
    }
    
    fun exitSafetyMode() {
        if (!isInSafetyMode) return
        isInSafetyMode = false
        lastResponseTime = System.currentTimeMillis()
        onSafetyModeExitCallback?.invoke()
        ttsManager.speak("网络已恢复，继续导航")
    }
    
    fun isInSafety(): Boolean = isInSafetyMode
    
    fun stopMonitoring() {
        safetyJob?.cancel()
        safetyJob = null
        isInSafetyMode = false
    }
    
    fun reset() {
        lastResponseTime = System.currentTimeMillis()
        isInSafetyMode = false
    }
}
