package com.example.data.repository

import android.util.Log
import com.example.data.model.CreateExamRequest
import com.example.data.model.Exam
import com.example.data.model.ExamType
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    } catch (e: CancellationException) {
      throw e
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
    } catch (e: CancellationException) {
      throw e
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

      // 1. Verify exam exists and belongs to the authenticated teacher before touching Storage
      val existingExams = SupabaseClientProvider.client.postgrest["exams"]
        .select(Columns.list("id")) {
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()

      if (existingExams.isEmpty()) {
        return@withContext Result.failure(
          IllegalStateException("Deletion failed. The exam was not found or access was denied.")
        )
      }

      // 2. Find all classes belonging to this exam BEFORE database cascade deletion
      val classRows = SupabaseClientProvider.client.postgrest["classes"]
        .select(Columns.list("id", "question_paper_path")) {
          filter {
            eq("exam_id", id)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<ClassStorageProjection>()

      if (classRows.isNotEmpty()) {
        val classIds = classRows.mapNotNull { it.id }.filter { it.isNotBlank() }

        // 3. Collect all student answer-sheet page paths/IDs and class question-paper paths concurrently BEFORE deletion
        val (cleanupTarget, questionPaperPaths) = coroutineScope {
          val studentTargetDeferred = async {
            StorageCleanupHelper.collectClassCleanupTarget(
              classIds = classIds,
              teacherId = teacherId,
            )
          }
          val questionPathsDeferred = async {
            StorageCleanupHelper.collectQuestionPaperPathsForClasses(
              classRooms = classRows,
              teacherId = teacherId,
            )
          }
          studentTargetDeferred.await() to questionPathsDeferred.await()
        }

        // 4. Delete student answer-sheet page files and class question-paper files from Storage FIRST
        StorageCleanupHelper.deleteFromBucket(
          bucketName = StorageCleanupHelper.STUDENT_PAPERS_BUCKET,
          rawPaths = cleanupTarget.studentPaperPaths,
        )
        StorageCleanupHelper.deleteFromBucket(
          bucketName = StorageCleanupHelper.QUESTION_PAPERS_BUCKET,
          rawPaths = questionPaperPaths,
        )

        // 5. Delete associated child records (reusing precollected studentIds & paperIds) and classes belonging to this exam
        StorageCleanupHelper.deleteClassChildRecords(
          classIds = classIds,
          teacherId = teacherId,
          precollectedStudentIds = cleanupTarget.studentIds,
          precollectedPaperIds = cleanupTarget.paperIds,
        )

        if (classIds.isNotEmpty()) {
          SupabaseClientProvider.client.postgrest["classes"]
            .delete {
              filter {
                isIn("id", classIds)
                eq("teacher_id", teacherId)
              }
            }
        }
      }

      // 6. Delete exam database record
      val deletedExams = SupabaseClientProvider.client.postgrest["exams"]
        .delete {
          select(Columns.list("id"))
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()

      if (deletedExams.isEmpty()) {
        return@withContext Result.failure(
          IllegalStateException("Deletion failed. The exam was not found or access was denied.")
        )
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting exam with id: $id", e)
      Result.failure(e)
    }
  }
}
