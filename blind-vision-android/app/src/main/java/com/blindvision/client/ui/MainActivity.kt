package com.blindvision.client.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blindvision.client.BlindVisionApp
import com.blindvision.client.camera.CameraController
import com.blindvision.client.data.model.RiskLevel
import com.blindvision.client.data.model.VisionState
import com.blindvision.client.databinding.ActivityMainBinding
import com.blindvision.client.domain.DecisionScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var decisionScheduler: DecisionScheduler
    private lateinit var cameraController: CameraController
    
    private var isListening = false
    
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startCamera()
            preflightServerConnection()
        } else {
            Toast.makeText(this, "需要相机和麦克风权限", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        decisionScheduler = (application as BlindVisionApp).decisionScheduler
        
        checkPermissions()
        setupUI()
        observeState()
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            startCamera()
            preflightServerConnection()
        } else {
            requestPermissions.launch(permissions)
        }
    }

    private fun preflightServerConnection() {
        lifecycleScope.launch {
            decisionScheduler.preflightServerConnection()
        }
    }
    
    private fun startCamera() {
        cameraController = CameraController(this, this, binding.previewView)
        decisionScheduler.setCameraController(cameraController)
        cameraController.setErrorCallback { error ->
            runOnUiThread {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }
        cameraController.startCamera()
    }
    
    private fun setupUI() {
        binding.btnStart.setOnClickListener {
            lifecycleScope.launch {
                if (decisionScheduler.visionState.value != VisionState.Navigating) {
                    decisionScheduler.startNavigation()
                }
            }
        }
        
        binding.btnStop.setOnClickListener {
            decisionScheduler.stopNavigation()
            isListening = false
            binding.btnVoice.text = "语音提问"
        }
        
        binding.btnVoice.setOnClickListener {
            if (isListening) {
                decisionScheduler.stopVoiceRecognition()
                isListening = false
                binding.btnVoice.text = "语音提问"
            } else {
                decisionScheduler.startVoiceRecognition()
                isListening = true
                binding.btnVoice.text = "正在听..."
            }
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            decisionScheduler.visionState.collect { state ->
                updateUIForState(state)
            }
        }
        
        lifecycleScope.launch {
            decisionScheduler.riskLevel.collect { level ->
                updateRiskIndicator(level)
            }
        }
        
        lifecycleScope.launch {
            decisionScheduler.message.collect { message ->
                if (message.isNotEmpty()) {
                    binding.tvMessage.text = message
                }
            }
        }
        
        lifecycleScope.launch {
            decisionScheduler.state.collect { state ->
                binding.tvSession.text = "会话: ${state.sessionId?.take(8) ?: "无"}..."
                binding.tvRound.text = "轮次: ${if (state.isNavigating) "进行中" else "未开始"}"
            }
        }
    }
    
    private fun updateUIForState(state: VisionState) {
        when (state) {
            is VisionState.Idle -> {
                binding.tvStatus.text = "状态: 待机"
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.btnStop.visibility = android.view.View.GONE
                binding.btnVoice.isEnabled = false
                binding.btnVoice.text = "语音提问"
                isListening = false
            }
            is VisionState.Navigating -> {
                binding.tvStatus.text = "状态: 导航中"
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = true
                binding.btnStop.visibility = android.view.View.VISIBLE
                binding.btnVoice.isEnabled = true
            }
            is VisionState.SafetyMode -> {
                binding.tvStatus.text = "状态: 安全模式"
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = true
                binding.btnStop.visibility = android.view.View.VISIBLE
                binding.btnVoice.isEnabled = true
            }
            is VisionState.HighRisk -> {
                binding.tvStatus.text = "状态: 高风险"
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = true
                binding.btnStop.visibility = android.view.View.VISIBLE
                binding.btnVoice.isEnabled = true
            }
            is VisionState.Error -> {
                binding.tvStatus.text = "状态: 错误"
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.btnStop.visibility = android.view.View.GONE
                binding.btnVoice.isEnabled = false
                binding.btnVoice.text = "语音提问"
                isListening = false
            }
        }
    }
    
    private fun updateRiskIndicator(level: RiskLevel) {
        val color = when (level) {
            RiskLevel.HIGH -> getColor(android.R.color.holo_red_dark)
            RiskLevel.MEDIUM -> getColor(android.R.color.holo_orange_dark)
            RiskLevel.LOW -> getColor(android.R.color.holo_green_dark)
        }
        binding.tvRiskLevel.setTextColor(color)
        binding.tvRiskLevel.text = "风险: ${level.name}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::cameraController.isInitialized) {
            cameraController.stopCamera()
        }
    }
}
