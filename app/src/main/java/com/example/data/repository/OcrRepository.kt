package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ProcessOcrRequest
import com.example.data.model.ProcessOcrResponse
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class CancellableHttpResponse(
  val code: Int,
  val isSuccessful: Boolean,
  val body: String,
)

internal suspend fun OkHttpClient.executeCancellable(
  request: Request,
): CancellableHttpResponse = suspendCancellableCoroutine { continuation ->
  val call = newCall(request)
  continuation.invokeOnCancellation {
    call.cancel()
  }
  if (continuation.isCancelled) {
    call.cancel()
    return@suspendCancellableCoroutine
  }
  call.enqueue(object : Callback {
    override fun onFailure(call: Call, e: IOException) {
      if (call.isCanceled() || !continuation.isActive) {
        if (continuation.isActive) {
          continuation.cancel(CancellationException("HTTP request was cancelled", e))
        }
        return
      }
      continuation.resumeWithException(e)
    }

    override fun onResponse(call: Call, response: Response) {
      response.use { resp ->
        try {
          val bodyString = resp.body?.string().orEmpty()
          if (call.isCanceled() || !continuation.isActive) {
            if (continuation.isActive) {
              continuation.cancel(CancellationException("HTTP request was cancelled"))
            }
            return
          }
          continuation.resume(
            CancellableHttpResponse(
              code = resp.code,
              isSuccessful = resp.isSuccessful,
              body = bodyString,
            )
          )
        } catch (e: CancellationException) {
          call.cancel()
          if (continuation.isActive) {
            continuation.cancel(e)
          }
        } catch (e: Throwable) {
          if (call.isCanceled() || !continuation.isActive) {
            if (continuation.isActive) {
              continuation.cancel(CancellationException("HTTP request was cancelled", e))
            }
          } else {
            continuation.resumeWithException(e)
          }
        }
      }
    }
  })
}

interface OcrRepository {
  suspend fun processPageOcr(paperId: String, pageId: String): Result<String>
}

class SupabaseOcrRepository(
  private val authRepository: AuthRepository,
  private val httpClient: OkHttpClient = SupabaseClientProvider.baseOkHttpClient.newBuilder()
    .connectTimeout(45, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(45, TimeUnit.SECONDS)
    .build(),
  private val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  },
  private val accessTokenProvider: (() -> String?)? = null,
) : OcrRepository {
  private val TAG = "OcrRepository"

  override suspend fun processPageOcr(paperId: String, pageId: String): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        val accessToken = if (accessTokenProvider != null) {
          accessTokenProvider.invoke()
        } else {
          if (!SupabaseClientProvider.isConfigured()) {
            return@withContext Result.failure(
              IllegalStateException("Supabase is not configured yet. Please check your Supabase credentials.")
            )
          }
          val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
          session?.accessToken
        }

        if (accessToken.isNullOrBlank()) {
          return@withContext Result.failure(
            IllegalStateException("Teacher is not authenticated or session has expired. Please sign in again.")
          )
        }

        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/').ifBlank { "https://example.supabase.co" }
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

        val response = httpClient.executeCancellable(httpRequest)
        val responseBodyString = response.body

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
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Network exception calling process-paper-ocr", e)
        Result.failure(e)
      }
    }
}
