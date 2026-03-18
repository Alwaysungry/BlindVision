package com.blindvision.client.data.remote

import com.blindvision.client.data.model.ApiResponse
import com.blindvision.client.data.model.SessionStartResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ApiClient(private val baseUrl: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private var sessionId: String? = null
    
    fun setSessionId(id: String) {
        sessionId = id
    }
    
    fun getSessionId(): String? = sessionId
    
    suspend fun startSession(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/session/start")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val result = gson.fromJson(body, SessionStartResponse::class.java)
                sessionId = result.session_id
                result.session_id
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun sendFrame(base64Data: String, frameId: String): ApiResponse? = withContext(Dispatchers.IO) {
        try {
            val id = sessionId ?: return@withContext null
            
            val requestBody = gson.toJson(mapOf(
                "frames" to listOf(base64Data),
                "frame_ids" to listOf(frameId)
            ))
            
            val request = Request.Builder()
                .url("$baseUrl/session/$id/frame")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                gson.fromJson(response.body?.string(), ApiResponse::class.java)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun sendQuery(query: String, frames: List<String>, frameIds: List<String>): ApiResponse? = withContext(Dispatchers.IO) {
        try {
            val id = sessionId ?: return@withContext null
            
            val requestBody = gson.toJson(mapOf(
                "query" to query,
                "frames" to frames,
                "frame_ids" to frameIds
            ))
            
            val request = Request.Builder()
                .url("$baseUrl/session/$id/query")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                gson.fromJson(response.body?.string(), ApiResponse::class.java)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    fun cleanup() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
