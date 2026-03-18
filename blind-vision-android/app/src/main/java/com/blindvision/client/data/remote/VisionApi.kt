package com.blindvision.client.data.remote

import com.blindvision.client.data.model.ApiResponse
import com.blindvision.client.data.model.SessionStartResponse
import retrofit2.Response
import retrofit2.http.*

interface VisionApi {
    @POST("/session/start")
    suspend fun startSession(): Response<SessionStartResponse>

    @POST("/session/{sessionId}/frame")
    suspend fun sendFrame(
        @Path("sessionId") sessionId: String,
        @Body request: FrameRequest
    ): Response<ApiResponse>

    @POST("/session/{sessionId}/query")
    suspend fun sendQuery(
        @Path("sessionId") sessionId: String,
        @Body request: QueryRequest
    ): Response<ApiResponse>

    @GET("/session/{sessionId}/status")
    suspend fun getStatus(@Path("sessionId") sessionId: String): Response<Map<String, Any>>

    @GET("/health")
    suspend fun healthCheck(): Response<Map<String, String>>
}

data class FrameRequest(
    val frames: List<String>,
    val frame_ids: List<String>? = null
)

data class QueryRequest(
    val query: String,
    val frames: List<String>,
    val frame_ids: List<String>? = null
)
