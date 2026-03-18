package com.blindvision.client.voice

enum class VoiceCommand {
    START, STOP, CROSS_ROAD, CHECK_PATH, SCAN_ENVIRONMENT,
    CHECK_TRAFFIC_LIGHT, CHECK_OBSTACLE, CHECK_STAIR, UNKNOWN
}

enum class IntentType {
    CROSS_ROAD, PATH_CHECK, ENVIRONMENT_SCAN, TRAFFIC_LIGHT_CHECK,
    OBSTACLE_CHECK, STAIR_CHECK, GENERAL_QUESTION
}

object IntentParser {
    
    fun detectCommand(text: String): VoiceCommand {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("开始") || lowerText.contains("启动") -> VoiceCommand.START
            lowerText.contains("停止") || lowerText.contains("结束") -> VoiceCommand.STOP
            lowerText.contains("过马路") -> VoiceCommand.CROSS_ROAD
            lowerText.contains("路况") || lowerText.contains("能不能走") -> VoiceCommand.CHECK_PATH
            lowerText.contains("环境") || lowerText.contains("周围") -> VoiceCommand.SCAN_ENVIRONMENT
            lowerText.contains("红绿灯") -> VoiceCommand.CHECK_TRAFFIC_LIGHT
            lowerText.contains("障碍") -> VoiceCommand.CHECK_OBSTACLE
            lowerText.contains("台阶") || lowerText.contains("楼梯") -> VoiceCommand.CHECK_STAIR
            else -> VoiceCommand.UNKNOWN
        }
    }
    
    fun detectIntent(text: String): IntentType {
        return when (detectCommand(text)) {
            VoiceCommand.CROSS_ROAD -> IntentType.CROSS_ROAD
            VoiceCommand.CHECK_PATH -> IntentType.PATH_CHECK
            VoiceCommand.SCAN_ENVIRONMENT -> IntentType.ENVIRONMENT_SCAN
            VoiceCommand.CHECK_TRAFFIC_LIGHT -> IntentType.TRAFFIC_LIGHT_CHECK
            VoiceCommand.CHECK_OBSTACLE -> IntentType.OBSTACLE_CHECK
            VoiceCommand.CHECK_STAIR -> IntentType.STAIR_CHECK
            else -> IntentType.GENERAL_QUESTION
        }
    }
}
