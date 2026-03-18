package com.blindvision.client.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.blindvision.client.data.model.FrameData
import com.blindvision.client.data.model.RiskLevel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    companion object {
        private const val TAG = "CameraController"
        private const val FRAME_WIDTH = 640
        private const val FRAME_HEIGHT = 480
        private const val JPEG_QUALITY = 80
    }
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var frameCallback: ((FrameData) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    
    private var currentFps: Int = 1
    private var frameCounter: Int = 0
    private var isCapturing: Boolean = false
    private var lastFrameSentAtMs: Long = 0L
    
    fun setFrameCallback(callback: (FrameData) -> Unit) {
        this.frameCallback = callback
    }
    
    fun setErrorCallback(callback: (String) -> Unit) {
        this.errorCallback = callback
    }
    
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}")
                errorCallback?.invoke("Camera start failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        
        // Preview
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image Analysis
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(FRAME_WIDTH, FRAME_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }
        
        cameraProvider?.unbindAll()
        
        camera = cameraProvider?.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }
    
    private fun processFrame(imageProxy: ImageProxy) {
        if (!isCapturing) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        val minIntervalMs = 1000L / currentFps.coerceAtLeast(1)
        if (now - lastFrameSentAtMs < minIntervalMs) {
            imageProxy.close()
            return
        }
        lastFrameSentAtMs = now
        
        try {
            val base64Data = imageProxyToBase64(imageProxy)
            if (base64Data != null) {
                val frameData = FrameData(
                    frameId = generateFrameId(),
                    base64Data = base64Data,
                    timestamp = System.currentTimeMillis()
                )
                frameCallback?.invoke(frameData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }
    
    private fun imageProxyToBase64(image: ImageProxy): String? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), JPEG_QUALITY, outputStream)
        
        val jpegBytes = outputStream.toByteArray()
        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }
    
    private fun generateFrameId(): String {
        frameCounter++
        return "frame_${System.currentTimeMillis()}_$frameCounter"
    }
    
    fun startAutoCapture() {
        isCapturing = true
        lastFrameSentAtMs = 0L
        Log.i(TAG, "Auto capture started")
    }
    
    fun stopAutoCapture() {
        isCapturing = false
        Log.i(TAG, "Auto capture stopped")
    }
    
    fun setFrameRate(fps: Int) {
        currentFps = fps.coerceIn(1, 3)
        // CameraX handles frame rate automatically based on processing speed
        Log.i(TAG, "Frame rate set to $currentFps fps")
    }
    
    fun adjustFrameRateByRisk(riskLevel: RiskLevel) {
        when (riskLevel) {
            RiskLevel.HIGH -> setFrameRate(3)
            RiskLevel.MEDIUM -> setFrameRate(2)
            RiskLevel.LOW -> setFrameRate(1)
        }
    }
    
    fun stopCamera() {
        stopAutoCapture()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        Log.i(TAG, "Camera stopped")
    }
    
    fun getCurrentFps(): Int = currentFps
    
    fun isCameraActive(): Boolean = cameraProvider != null && isCapturing
}
