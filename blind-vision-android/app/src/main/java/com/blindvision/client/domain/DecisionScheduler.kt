package com.blindvision.client.domain

import android.content.Context
import com.blindvision.client.camera.CameraController
import com.blindvision.client.data.model.*
import com.blindvision.client.data.remote.ApiClient
import com.blindvision.client.safety.SafetyManager
import com.blindvision.client.sensor.MotionMonitor
import com.blindvision.client.voice.IntentParser
import com.blindvision.client.voice.TTSManager
import com.blindvision.client.voice.VoiceCommand
import com.blindvision.client.voice.VoiceController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class DecisionScheduler(private val context: Context) {

    private enum class NextStep {
        STOP_AND_WARN,
        CAUTION,
        CONTINUE
    }
    
    companion object {
        private const val TAG = "DecisionScheduler"
        private const val ANTI_SPAM_INTERVAL_MS = 3000L
        private const val HEARTBEAT_INTERVAL_MS = 5000L
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val apiClient = ApiClient("http://192.168.3.68:8000")
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceController: VoiceController
    private lateinit var motionMonitor: MotionMonitor
    private lateinit var safetyManager: SafetyManager
    private var cameraController: CameraController? = null
    
    private val _state = MutableStateFlow<ClientState>(ClientState())
    val state: StateFlow<ClientState> = _state
    
    private val _visionState = MutableStateFlow<VisionState>(VisionState.Idle)
    val visionState: StateFlow<VisionState> = _visionState
    
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message
    
    private val _riskLevel = MutableStateFlow(RiskLevel.LOW)
    val riskLevel: StateFlow<RiskLevel> = _riskLevel

    private val _serverConnected = MutableStateFlow(false)
    val serverConnected: StateFlow<Boolean> = _serverConnected
    
    private var isProcessing = false
    private var heartbeatJob: Job? = null
    private val navigationMutex = Mutex()
    
    fun initialize() {
        ttsManager = TTSManager(context)
        voiceController = VoiceController(context)
        motionMonitor = MotionMonitor(context)
        safetyManager = SafetyManager(ttsManager)
        
        setupCallbacks()
    }
    
    private fun setupCallbacks() {
        motionMonitor.setOnHighRiskMovementCallback {
            safetyManager.handleHighRiskMovement()
        }
        
        voiceController.setCallbacks(
            onResult = { text -> handleVoiceResult(text) },
            onStart = {},
            onEnd = {},
            onError = {}
        )
        
        safetyManager.setCallbacks(
            onEnter = { _visionState.value = VisionState.SafetyMode },
            onExit = { _visionState.value = VisionState.Navigating },
            onEvent = {}
        )
    }
    
    fun setCameraController(controller: CameraController) {
        cameraController = controller
        cameraController?.setFrameCallback { frame ->
            handleFrameCaptured(frame)
        }
    }

    suspend fun preflightServerConnection(): Boolean {
        _message.value = "正在检查服务器连接..."
        val connected = apiClient.healthCheck()
        _serverConnected.value = connected
        _message.value = if (connected) {
            "服务器已连接，可开始导航"
        } else {
            "服务器不可用，请检查网络后重试"
        }
        return connected
    }
    
    suspend fun startNavigation(): Boolean = navigationMutex.withLock {
        if (_state.value.isNavigating) return@withLock true

        _message.value = "正在建立导航会话..."
        if (!_serverConnected.value) {
            val connected = apiClient.healthCheck()
            _serverConnected.value = connected
            if (!connected) {
                _visionState.value = VisionState.Idle
                _message.value = "服务器不可用，请检查网络后重试"
                ttsManager.speak("网络异常，无法开始导航")
                return@withLock false
            }
        }

        val sessionId = apiClient.startSession()
        if (sessionId == null) {
            _serverConnected.value = false
            _visionState.value = VisionState.Idle
            _message.value = "会话创建失败，请稍后重试"
            ttsManager.speak("连接失败，请稍后重试")
            return@withLock false
        }

        _state.update {
            it.copy(
                sessionId = sessionId,
                isNavigating = true,
                currentState = "OBSERVE"
            )
        }

        motionMonitor.startMonitoring()
        safetyManager.startMonitoring()
        startHeartbeat()
        cameraController?.startAutoCapture()

        _visionState.value = VisionState.Navigating
        _message.value = "导航已开始，正在实时分析"
        ttsManager.speak("导航已开始")
        true
    }
    
    fun stopNavigation() {
        if (!_state.value.isNavigating && !safetyManager.isInSafety()) return
        
        cameraController?.stopAutoCapture()
        voiceController.stopListening()
        motionMonitor.stopMonitoring()
        safetyManager.stopMonitoring()
        stopHeartbeat()
        
        _state.update { it.copy(
            isNavigating = false,
            sessionId = null,
            currentState = "INIT"
        )}
        
        _visionState.value = VisionState.Idle
        _message.value = "导航已停止"
        ttsManager.speak("导航已停止")
    }
    
    private fun handleFrameCaptured(frame: FrameData) {
        val currentState = _state.value
        if (!currentState.isNavigating || isProcessing) return
        
        safetyManager.recordActivity()
        _state.update { it.copy(lastFrameId = frame.frameId) }
        
        scope.launch {
            processFrame(frame)
        }
    }
    
    private suspend fun processFrame(frame: FrameData) {
        isProcessing = true
        try {
            val response = apiClient.sendFrame(frame.base64Data, frame.frameId)
            if (response != null) {
                handleApiResponse(response)
            } else {
                _serverConnected.value = false
                safetyManager.handleNetworkDisconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _serverConnected.value = false
            safetyManager.handleNetworkDisconnect()
        } finally {
            isProcessing = false
        }
    }
    
    private fun handleApiResponse(response: ApiResponse) {
        safetyManager.recordResponse()
        
        val lastFrameId = _state.value.lastFrameId
        if (response.frame_ids != null && lastFrameId != null) {
            if (!response.frame_ids.contains(lastFrameId)) return
        }
        
        _state.update { it.copy(
            currentState = response.state,
            lastRiskLevel = response.risk_level
        )}
        _serverConnected.value = true
        
        _riskLevel.value = response.risk_level
        
        motionMonitor.setRiskLevel(response.risk_level)
        cameraController?.adjustFrameRateByRisk(response.risk_level)

        val nextStep = decideNextStep(response)
        val spokenMessage = composeGuidanceMessage(response, nextStep)

        if (nextStep == NextStep.STOP_AND_WARN) {
            _visionState.value = VisionState.HighRisk(spokenMessage)
        } else if (_state.value.isNavigating) {
            _visionState.value = VisionState.Navigating
        }
        
        if (shouldSpeakMessage(spokenMessage, response.risk_level)) {
            val interrupt = nextStep == NextStep.STOP_AND_WARN
            ttsManager.speak(spokenMessage, interrupt)
            
            _state.update { it.copy(
                lastSpokenMessage = spokenMessage,
                lastSpokenTime = System.currentTimeMillis()
            )}
            _message.value = spokenMessage
        }
    }
    
    private fun shouldSpeakMessage(message: String, riskLevel: RiskLevel): Boolean {
        if (riskLevel == RiskLevel.HIGH) return true

        val state = _state.value
        if (message == state.lastSpokenMessage) {
            if (System.currentTimeMillis() - state.lastSpokenTime < ANTI_SPAM_INTERVAL_MS) {
                return false
            }
        }
        return true
    }

    private fun decideNextStep(response: ApiResponse): NextStep {
        if (response.risk_level == RiskLevel.HIGH) return NextStep.STOP_AND_WARN

        if (response.risk_detected && response.affects_path) {
            return if (response.distance_estimate == DistanceEstimate.NEAR) {
                NextStep.STOP_AND_WARN
            } else {
                NextStep.CAUTION
            }
        }

        if (response.risk_level == RiskLevel.MEDIUM || response.distance_estimate == DistanceEstimate.NEAR) {
            return NextStep.CAUTION
        }

        if (response.confidence < 0.45f) {
            return NextStep.CAUTION
        }

        return NextStep.CONTINUE
    }

    private fun composeGuidanceMessage(response: ApiResponse, nextStep: NextStep): String {
        val baseMessage = response.assistant_message.trim()
        if (baseMessage.isNotEmpty()) {
            return if (response.confidence < 0.45f && nextStep != NextStep.STOP_AND_WARN) {
                "$baseMessage，判断不确定，建议放慢并再次确认"
            } else {
                baseMessage
            }
        }

        val directionText = when (response.direction.lowercase(Locale.ROOT)) {
            "left" -> "向左"
            "right" -> "向右"
            "forward", "ahead" -> "向前"
            "backward", "back" -> "向后"
            "stop", "stay" -> "停止"
            else -> "保持当前方向"
        }

        return when (nextStep) {
            NextStep.STOP_AND_WARN -> "前方存在高风险，请先停止移动"
            NextStep.CAUTION -> "前方存在不确定风险，建议${directionText}并谨慎通行"
            NextStep.CONTINUE -> "当前路径可通行，可继续前进"
        }
    }
    
    private fun handleVoiceResult(text: String) {
        val command = IntentParser.detectCommand(text)
        
        when (command) {
            VoiceCommand.START -> {
                ttsManager.speak("请点击屏幕开始导航")
            }
            VoiceCommand.STOP -> {
                stopNavigation()
            }
            else -> {
                if (_state.value.isNavigating) {
                    ttsManager.speak("正在分析")
                    scope.launch {
                        try {
                            val response = apiClient.sendQuery(text, emptyList(), emptyList())
                            if (response != null) {
                                handleApiResponse(response)
                            } else {
                                _serverConnected.value = false
                                ttsManager.speak("网络连接失败，请检查网络")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _serverConnected.value = false
                            ttsManager.speak("网络连接失败，请检查网络")
                        }
                    }
                } else {
                    ttsManager.speak("请先开始导航")
                }
            }
        }
    }
    
    fun startVoiceRecognition() {
        voiceController.startListening()
    }
    
    fun stopVoiceRecognition() {
        voiceController.stopListening()
    }
    
    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            var lastAnnouncementTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val timeSinceLastAnnouncement = System.currentTimeMillis() - lastAnnouncementTime
                if (timeSinceLastAnnouncement >= HEARTBEAT_INTERVAL_MS) {
                    if (!ttsManager.isEngineSpeaking()) {
                        ttsManager.speak("环境正常", false)
                    }
                    lastAnnouncementTime = System.currentTimeMillis()
                }
            }
        }
    }
    
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    fun cleanup() {
        stopNavigation()
        ttsManager.shutdown()
        voiceController.shutdown()
        cameraController?.stopCamera()
        apiClient.cleanup()
        scope.cancel()
    }
}
