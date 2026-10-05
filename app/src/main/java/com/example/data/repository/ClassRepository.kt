package com.example.data.repository

import android.util.Log
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.model.CreateClassRequest
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface ClassRepository {
  suspend fun getClassesByExamId(examId: String): Result<List<ClassRoom>>
  suspend fun getClassById(classId: String): Result<ClassRoom?>
  suspend fun createClass(
    examId: String,
    session: String,
    semester: String,
    timing: ClassTiming,
    subjectName: String,
    totalMarks: Int,
    questionPaperBytes: ByteArray?,
    fileExtension: String?,
  ): Result<ClassRoom>
  suspend fun deleteClass(id: String): Result<Unit>
}

class SupabaseClassRepository(
  private val authRepository: AuthRepository,
) : ClassRepository {
  private val TAG = "ClassRepository"
  private val BUCKET_NAME = "question-papers"

  override suspend fun getClassesByExamId(examId: String): Result<List<ClassRoom>> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val classes = SupabaseClientProvider.client.postgrest["classes"]
          .select {
            filter {
              eq("exam_id", examId)
              eq("teacher_id", teacherId)
            }
            order("created_at", order = Order.ASCENDING)
          }
          .decodeList<ClassRoom>()

        Result.success(classes)
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching classes for exam $examId", e)
        Result.failure(e)
      }
    }

  override suspend fun getClassById(classId: String): Result<ClassRoom?> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val classRoom = SupabaseClientProvider.client.postgrest["classes"]
          .select {
            filter {
              eq("id", classId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeSingleOrNull<ClassRoom>()

        Result.success(classRoom)
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching class with id: $classId", e)
        Result.failure(e)
      }
    }

  override suspend fun createClass(
    examId: String,
    session: String,
    semester: String,
    timing: ClassTiming,
    subjectName: String,
    totalMarks: Int,
    questionPaperBytes: ByteArray?,
    fileExtension: String?,
  ): Result<ClassRoom> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      var questionPaperStoragePath: String? = null

      if (questionPaperBytes != null && questionPaperBytes.isNotEmpty()) {
        val ext = fileExtension ?: "jpg"
        val storagePath = "$teacherId/$examId/${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext"

        Log.d(TAG, "Uploading question paper to private storage path: $storagePath")
        val bucket = SupabaseClientProvider.client.storage.from(BUCKET_NAME)
        bucket.upload(storagePath, questionPaperBytes)
        questionPaperStoragePath = storagePath
      }

      val request = CreateClassRequest(
        examId = examId,
        teacherId = teacherId,
        session = session.trim(),
        semester = semester.trim(),
        timing = timing.displayName,
        subjectName = subjectName.trim(),
        totalMarks = totalMarks,
        questionPaperPath = questionPaperStoragePath,
      )

      val insertedClass = SupabaseClientProvider.client.postgrest["classes"]
        .insert(request) {
          select()
        }
        .decodeSingle<ClassRoom>()

      Result.success(insertedClass)
    } catch (e: Exception) {
      Log.e(TAG, "Error creating class for exam $examId", e)
      Result.failure(e)
    }
  }

  override suspend fun deleteClass(id: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      SupabaseClientProvider.client.postgrest["classes"]
        .delete {
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting class with id: $id", e)
      Result.failure(e)
    }
  }
}
