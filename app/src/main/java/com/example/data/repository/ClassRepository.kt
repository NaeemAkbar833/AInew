package com.example.data.repository

import android.util.Log
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.model.CreateClassRequest
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class ClassCountIdRow(
  @SerialName("id")
  val id: String? = null,
)

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
  suspend fun getClassCountForTeacher(): Result<Int>
}

class SupabaseClassRepository(
  private val authRepository: AuthRepository,
) : ClassRepository {
  private val TAG = "ClassRepository"
  private val BUCKET_NAME = StorageCleanupHelper.QUESTION_PAPERS_BUCKET

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
      } catch (e: CancellationException) {
        throw e
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
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching class with id: $classId", e)
        Result.failure(e)
      }
    }

  override suspend fun getClassCountForTeacher(): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val result = SupabaseClientProvider.client.postgrest["classes"]
          .select(Columns.list("id")) {
            count(Count.EXACT)
            filter {
              eq("teacher_id", teacherId)
            }
          }

        val count = result.countOrNull()?.toInt()
          ?: result.decodeList<ClassCountIdRow>().size

        Result.success(count)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching class count for teacher", e)
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

      val insertedClass = try {
        SupabaseClientProvider.client.postgrest["classes"]
          .insert(request) {
            select()
          }
          .decodeSingle<ClassRoom>()
      } catch (dbError: Exception) {
        if (!questionPaperStoragePath.isNullOrBlank()) {
          runCatching {
            SupabaseClientProvider.client.storage.from(BUCKET_NAME)
              .delete(listOf(questionPaperStoragePath))
          }
        }
        throw dbError
      }

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

      // 1. Fetch class record to verify ownership and obtain question_paper_path BEFORE cascade deletion
      val classRows = SupabaseClientProvider.client.postgrest["classes"]
        .select(Columns.list("id", "question_paper_path")) {
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<ClassStorageProjection>()

      if (classRows.isEmpty()) {
        return@withContext Result.failure(
          IllegalStateException("Deletion failed. The class was not found or access was denied.")
        )
      }

      // 2. Collect student paper paths/IDs and question paper paths concurrently BEFORE any deletion
      val (cleanupTarget, questionPaperPaths) = coroutineScope {
        val studentTargetDeferred = async {
          StorageCleanupHelper.collectClassCleanupTarget(
            classIds = listOf(id),
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

      // 3. Delete student paper page images and question paper file(s) from Storage FIRST
      StorageCleanupHelper.deleteFromBucket(
        bucketName = StorageCleanupHelper.STUDENT_PAPERS_BUCKET,
        rawPaths = cleanupTarget.studentPaperPaths,
      )
      StorageCleanupHelper.deleteFromBucket(
        bucketName = BUCKET_NAME,
        rawPaths = questionPaperPaths,
      )

      // 4. Delete associated child records (reusing precollected studentIds & paperIds) and class database record
      StorageCleanupHelper.deleteClassChildRecords(
        classIds = listOf(id),
        teacherId = teacherId,
        precollectedStudentIds = cleanupTarget.studentIds,
        precollectedPaperIds = cleanupTarget.paperIds,
      )

      val deletedClasses = SupabaseClientProvider.client.postgrest["classes"]
        .delete {
          select(Columns.list("id"))
          filter {
            eq("id", id)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()

      if (deletedClasses.isEmpty()) {
        return@withContext Result.failure(
          IllegalStateException("Deletion failed. The class was not found or access was denied.")
        )
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error deleting class with id: $id", e)
      Result.failure(e)
    }
  }
}
