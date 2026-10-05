package com.example.data.repository

import android.util.Log
import com.example.data.model.CreateExamRequest
import com.example.data.model.Exam
import com.example.data.model.ExamType
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ExamRepository {
  suspend fun getExams(): Result<List<Exam>>
  suspend fun getExamById(id: String): Result<Exam?>
  suspend fun createExam(name: String, type: ExamType, date: String): Result<Exam>
  suspend fun deleteExam(id: String): Result<Unit>
}

class SupabaseExamRepository(
  private val authRepository: AuthRepository,
) : ExamRepository {
  private val TAG = "ExamRepository"

  override suspend fun getExams(): Result<List<Exam>> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet. Please configure your project keys.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      val exams = SupabaseClientProvider.client.postgrest["exams"]
        .select {
          filter {
            eq("teacher_id", teacherId)
          }
          order("created_at", order = Order.DESCENDING)
        }
        .decodeList<Exam>()

      Result.success(exams)
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching exams", e)
      Result.failure(e)
    }
  }

  override suspend fun getExamById(id: String): Result<Exam?> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val exam = SupabaseClientProvider.client.postgrest["exams"]
        .select {
          filter {
            eq("id", id)
          }
        }
        .decodeSingleOrNull<Exam>()

      Result.success(exam)
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching exam with id: $id", e)
      Result.failure(e)
    }
  }

  override suspend fun createExam(
    name: String,
    type: ExamType,
    date: String,
  ): Result<Exam> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      val request = CreateExamRequest(
        teacherId = teacherId,
        name = name.trim(),
        type = type.displayName,
        date = date.trim(),
      )

      val insertedExam = SupabaseClientProvider.client.postgrest["exams"]
        .insert(request) {
          select()
        }
        .decodeSingle<Exam>()

      Result.success(insertedExam)
    } catch (e: Exception) {
      Log.e(TAG, "Error creating exam", e)
      Result.failure(e)
    }
  }

  override suspend fun deleteExam(id: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet.")
        )
      }

      val teacherId = authRepository.getCurrentUserId()
        ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

      SupabaseClientProvider.client.postgrest["exams"]
        .delete {
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting exam with id: $id", e)
      Result.failure(e)
    }
  }
}
