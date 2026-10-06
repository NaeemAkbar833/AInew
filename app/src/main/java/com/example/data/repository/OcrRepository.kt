package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ProcessOcrRequest
import com.example.data.model.ProcessOcrResponse
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface OcrRepository {
  suspend fun processPageOcr(paperId: String, pageId: String): Result<String>
}

class SupabaseOcrRepository(
  private val authRepository: AuthRepository,
  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(45, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(45, TimeUnit.SECONDS)
    .build(),
  private val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  },
) : OcrRepository {
  private val TAG = "OcrRepository"

  override suspend fun processPageOcr(paperId: String, pageId: String): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet. Please check your Supabase credentials.")
          )
        }

        val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
        val accessToken = session?.accessToken
        if (accessToken.isNullOrBlank()) {
          return@withContext Result.failure(
            IllegalStateException("Teacher is not authenticated or session has expired. Please sign in again.")
          )
        }

        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/functions/v1/process-paper-ocr"
        val anonKey = BuildConfig.SUPABASE_ANON_KEY

        val requestPayload = ProcessOcrRequest(
          paperId = paperId,
          pageId = pageId,
        )
        val requestJsonString = json.encodeToString(ProcessOcrRequest.serializer(), requestPayload)
        val requestBody = requestJsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
          .url(endpoint)
          .post(requestBody)
          .header("Authorization", "Bearer $accessToken")
          .header("apikey", anonKey)
          .header("Content-Type", "application/json")
          .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
          val errorDetail = try {
            json.decodeFromString(ProcessOcrResponse.serializer(), responseBodyString).error
          } catch (e: Exception) {
            null
          }
          val message = errorDetail ?: "OCR processing failed with HTTP ${response.code}."
          Log.e(TAG, "process-paper-ocr returned status ${response.code}: $responseBodyString")
          return@withContext Result.failure(Exception(message))
        }

        val ocrResponse = json.decodeFromString(ProcessOcrResponse.serializer(), responseBodyString)
        if (ocrResponse.status == "completed" && !ocrResponse.ocrText.isNullOrBlank()) {
          Result.success(ocrResponse.ocrText)
        } else {
          val errorMsg = ocrResponse.error ?: "Transcription finished without any text detected."
          Result.failure(Exception(errorMsg))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Network exception calling process-paper-ocr", e)
        Result.failure(e)
      }
    }
}
