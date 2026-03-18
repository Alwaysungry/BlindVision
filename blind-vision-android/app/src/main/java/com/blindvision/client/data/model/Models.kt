package com.blindvision.client.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val session_id: String,
    val round: Int,
    val max_rounds: Int,
    val state: String,
    val frame_ids: List<String>? = null,
    
    val action: String,
    val direction: String,
    val environment_summary: String,
    val risk_detected: Boolean,
    val assistant_message: String,
    val confidence: Float,
    val risk_level: RiskLevel,
    val distance_estimate: DistanceEstimate,
    val affects_path: Boolean,
    
    val query: String? = null,
    val intent: String? = null
)

enum class RiskLevel {
    @SerializedName("low") LOW,
    @SerializedName("medium") MEDIUM,
    @SerializedName("high") HIGH;

    companion object {
        fun fromString(value: String): RiskLevel {
            return when (value.lowercase()) {
                "high" -> HIGH
                "medium" -> MEDIUM
                else -> LOW
            }
        }
    }
}

enum class DistanceEstimate {
    @SerializedName("near") NEAR,
    @SerializedName("mid") MID,
    @SerializedName("far") FAR,
    @SerializedName("none") NONE;

    companion object {
        fun fromString(value: String): DistanceEstimate {
            return when (value.lowercase()) {
                "near" -> NEAR
                "mid" -> MID
                "far" -> FAR
                else -> NONE
            }
        }
    }
}

data class FrameData(
    val frameId: String,
    val base64Data: String,
    val timestamp: Long
)

data class SafetyEvent(
    val type: String,
    val message: String,
    val timestamp: Long
)

data class SessionStartResponse(
    val session_id: String,
    val state: String,
    val message: String
)

data class ClientState(
    val sessionId: String? = null,
    val currentState: String = "INIT",
    val lastFrameId: String? = null,
    val lastRiskLevel: RiskLevel = RiskLevel.LOW,
    val lastSpokenMessage: String = "",
    val lastSpokenTime: Long = 0,
    val isNavigating: Boolean = false,
    val isMoving: Boolean = false,
    val networkConnected: Boolean = false,
    val batteryLevel: Int = 100
)

sealed class VisionState {
    object Idle : VisionState()
    object Navigating : VisionState()
    object SafetyMode : VisionState()
    object Error : VisionState()
    
    data class HighRisk(val message: String) : VisionState()
}
