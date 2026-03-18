package com.blindvision.client.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.blindvision.client.data.model.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MotionMonitor(context: Context) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private var isMonitoring = false
    private var currentRiskLevel: RiskLevel = RiskLevel.LOW
    
    private val _isMoving = MutableStateFlow(false)
    val isMoving: StateFlow<Boolean> = _isMoving
    
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    
    private var movementCounter = 0
    private val movementThreshold = 3
    private val motionThreshold = 2.0f
    
    private var onHighRiskMovementCallback: (() -> Unit)? = null
    
    fun setOnHighRiskMovementCallback(callback: () -> Unit) {
        onHighRiskMovementCallback = callback
    }
    
    fun setRiskLevel(riskLevel: RiskLevel) {
        currentRiskLevel = riskLevel
    }
    
    fun startMonitoring(): Boolean {
        if (isMonitoring) return true
        
        var registered = false
        
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            registered = true
        }
        
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
            registered = true
        }
        
        isMonitoring = registered
        return registered
    }
    
    fun stopMonitoring() {
        if (!isMonitoring) return
        sensorManager.unregisterListener(this)
        isMonitoring = false
        _isMoving.value = false
        movementCounter = 0
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometerData(event.values)
            Sensor.TYPE_GYROSCOPE -> processGyroscopeData(event.values)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun processAccelerometerData(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        val deltaX = kotlin.math.abs(x - lastAccelX)
        val deltaY = kotlin.math.abs(y - lastAccelY)
        val deltaZ = kotlin.math.abs(z - lastAccelZ)
        val totalDelta = deltaX + deltaY + deltaZ
        
        lastAccelX = x
        lastAccelY = y
        lastAccelZ = z
        
        if (totalDelta > motionThreshold) {
            movementCounter++
            if (movementCounter >= movementThreshold && !_isMoving.value) {
                _isMoving.value = true
                checkHighRiskMovement()
            }
        } else {
            movementCounter = maxOf(0, movementCounter - 1)
            if (movementCounter == 0 && _isMoving.value) {
                _isMoving.value = false
            }
        }
    }
    
    private fun processGyroscopeData(values: FloatArray) {
        val rotationMagnitude = kotlin.math.sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        
        if (rotationMagnitude > motionThreshold * 0.5f && !_isMoving.value) {
            movementCounter++
            if (movementCounter >= movementThreshold) {
                _isMoving.value = true
                checkHighRiskMovement()
            }
        }
    }
    
    private fun checkHighRiskMovement() {
        if (_isMoving.value && currentRiskLevel == RiskLevel.HIGH) {
            onHighRiskMovementCallback?.invoke()
        }
    }
    
    fun getMotionState(): Boolean = _isMoving.value
    
    fun reset() {
        movementCounter = 0
        _isMoving.value = false
        lastAccelX = 0f
        lastAccelY = 0f
        lastAccelZ = 0f
    }
}
