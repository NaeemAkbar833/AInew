package com.example.data.repository

import android.util.Log
import com.example.data.model.CreateStudentRequest
import com.example.data.model.Student
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import com.example.data.model.ExtractedStudent
import io.github.jan.supabase.auth.auth

interface StudentRepository {
  suspend fun getStudentsByClassId(classId: String): Result<List<Student>>
  suspend fun getStudentById(studentId: String): Result<Student?>
  suspend fun addStudent(
    classId: String,
    name: String,
    fatherName: String,
    rollNumber: String,
  ): Result<Student>
  suspend fun deleteStudent(studentId: String): Result<Unit>
  suspend fun extractStudentList(classId: String, images: List<String>): Result<List<ExtractedStudent>>
  suspend fun addStudentsBatch(classId: String, students: List<ExtractedStudent>): Result<Int>
}

class SupabaseStudentRepository(
  private val authRepository: AuthRepository,
) : StudentRepository {
  private val TAG = "StudentRepository"

  override suspend fun getStudentsByClassId(classId: String): Result<List<Student>> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val students = SupabaseClientProvider.client.postgrest["students"]
          .select {
            filter {
              eq("class_id", classId)
              eq("teacher_id", teacherId)
            }
            order("roll_number", order = Order.ASCENDING)
          }
          .decodeList<Student>()

        Result.success(students)
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching students for class $classId", e)
        Result.failure(e)
      }
    }

  override suspend fun getStudentById(studentId: String): Result<Student?> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val student = SupabaseClientProvider.client.postgrest["students"]
          .select {
            filter {
              eq("id", studentId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeSingleOrNull<Student>()

        Result.success(student)
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching student with id $studentId", e)
        Result.failure(e)
      }
    }

  override suspend fun addStudent(
    classId: String,
    name: String,
    fatherName: String,
    rollNumber: String,
  ): Result<Student> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      val request = CreateStudentRequest(
        classId = classId,
        teacherId = teacherId,
        name = name.trim(),
        fatherName = fatherName.trim(),
        rollNumber = rollNumber.trim(),
      )

      val insertedStudent = SupabaseClientProvider.client.postgrest["students"]
        .insert(request) {
          select()
        }
        .decodeSingle<Student>()

      Result.success(insertedStudent)
    } catch (e: Exception) {
      Log.e(TAG, "Error adding student to class $classId", e)
      val message = if (e.message?.contains("uq_class_roll_number", ignoreCase = true) == true ||
        e.message?.contains("duplicate key", ignoreCase = true) == true
      ) {
        "A student with roll number '$rollNumber' already exists in this class."
      } else {
        e.localizedMessage ?: "Failed to add student. Please try again."
      }
      Result.failure(IllegalStateException(message, e))
    }
  }

  override suspend fun deleteStudent(studentId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val deletedRows = SupabaseClientProvider.client.postgrest["students"]
          .delete {
            select()
            filter {
              eq("id", studentId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<Student>()

        if (deletedRows.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Deletion failed. The student was not found or access was denied.")
          )
        }

        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Error deleting student with id: $studentId", e)
        Result.failure(e)
      }
    }

  override suspend fun extractStudentList(classId: String, images: List<String>): Result<List<ExtractedStudent>> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }

        val accessToken = SupabaseClientProvider.client.auth.currentSessionOrNull()?.accessToken
        if (accessToken.isNullOrBlank()) {
          return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))
        }

        val baseUrl = com.example.BuildConfig.SUPABASE_URL.trimEnd('/')
        val endpoint = "$baseUrl/functions/v1/extract-student-list"
        val anonKey = com.example.BuildConfig.SUPABASE_ANON_KEY

        val requestPayload = com.example.data.model.ExtractStudentListRequest(classId, images)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val requestJsonString = json.encodeToString(com.example.data.model.ExtractStudentListRequest.serializer(), requestPayload)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJsonString.toRequestBody(mediaType)

        val httpRequest = okhttp3.Request.Builder()
          .url(endpoint)
          .post(requestBody)
          .header("Authorization", "Bearer $accessToken")
          .header("apikey", anonKey)
          .header("Content-Type", "application/json")
          .build()

        val httpClient = okhttp3.OkHttpClient.Builder()
          .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
          .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
          .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
          val errorMsg = try {
            json.decodeFromString(com.example.data.model.ExtractStudentListResponse.serializer(), responseBodyString).error
          } catch (e: Exception) {
            null
          } ?: "Extraction failed with HTTP ${response.code}."
          return@withContext Result.failure(Exception(errorMsg))
        }

        val extractResponse = json.decodeFromString(com.example.data.model.ExtractStudentListResponse.serializer(), responseBodyString)
        Result.success(extractResponse.students)
      } catch (e: Exception) {
        Log.e(TAG, "Error extracting student list", e)
        Result.failure(e)
      }
    }

  override suspend fun addStudentsBatch(classId: String, students: List<ExtractedStudent>): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val requests = students.map {
          CreateStudentRequest(
            classId = classId,
            teacherId = teacherId,
            name = it.name.trim(),
            fatherName = it.fatherName.trim(),
            rollNumber = it.rollNumber.trim(),
          )
        }

        val insertedStudents = SupabaseClientProvider.client.postgrest["students"]
          .insert(requests) {
            select()
          }
          .decodeList<Student>()

        Result.success(insertedStudents.size)
      } catch (e: Exception) {
        Log.e(TAG, "Error batch adding students", e)
        val message = if (e.message?.contains("uq_class_roll_number", ignoreCase = true) == true ||
          e.message?.contains("duplicate key", ignoreCase = true) == true
        ) {
          "Some roll numbers already exist in this class. Please review your list."
        } else {
          e.localizedMessage ?: "Failed to add students. Please try again."
        }
        Result.failure(IllegalStateException(message, e))
      }
    }
}
