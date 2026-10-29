package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.CreatePaperPageRequest
import com.example.data.model.CreatePaperRequest
import com.example.data.model.StudentPaper
import com.example.data.model.StudentPaperPage
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class IdRow(
  @SerialName("id")
  val id: String? = null,
)

@Serializable
internal data class StoragePathRow(
  @SerialName("storage_path")
  val storagePath: String? = null,
)

@Serializable
internal data class ClassStorageProjection(
  @SerialName("id")
  val id: String? = null,
  @SerialName("question_paper_path")
  val questionPaperPath: String? = null,
)

internal data class StudentCleanupTarget(
  val paperIds: List<String>,
  val storagePaths: List<String>,
)

internal data class ClassCleanupTarget(
  val studentIds: List<String>,
  val paperIds: List<String>,
  val studentPaperPaths: List<String>,
)

internal object StorageCleanupHelper {
  private const val TAG = "StorageCleanupHelper"
  const val QUESTION_PAPERS_BUCKET = "question-papers"
  const val STUDENT_PAPERS_BUCKET = "student-papers"

  fun normalizeStoragePaths(rawPaths: Iterable<String?>, bucketName: String): List<String> {
    return rawPaths
      .mapNotNull { raw ->
        val trimmed = raw?.trim()?.trimStart('/') ?: return@mapNotNull null
        if (trimmed.isBlank()) return@mapNotNull null
        val withoutBucket = if (trimmed.startsWith("$bucketName/")) {
          trimmed.removePrefix("$bucketName/").trimStart('/')
        } else {
          trimmed
        }
        withoutBucket.takeIf { it.isNotBlank() }
      }
      .distinct()
  }

  internal fun isAlreadyMissingStorageError(error: Throwable): Boolean {
    if (error !is RestException) return false
    if (error !is NotFoundRestException && error !is BadRequestRestException) return false
    if (error.statusCode != 404 && error.statusCode != 400) return false

    val structuredError = error.error.trim().lowercase()
    val structuredDescription = error.description?.trim()?.lowercase().orEmpty()
    val structuredMessage = error.message?.trim()?.lowercase().orEmpty()

    // Never treat a missing bucket as an already-deleted object
    if (structuredError.contains("bucket") ||
      structuredDescription.contains("bucket") ||
      structuredMessage.contains("bucket not found") ||
      structuredMessage.contains("nosuchbucket")
    ) {
      return false
    }

    val isObjectErrorCode = structuredError == "not_found" ||
      structuredError == "nosuchkey" ||
      structuredError == "object not found"
    val isObjectDescription = structuredDescription == "object not found" ||
      structuredDescription == "the resource was not found" ||
      structuredDescription == "the specified key does not exist" ||
      structuredMessage.contains("object not found") ||
      structuredMessage.contains("nosuchkey")

    return isObjectErrorCode || (error is NotFoundRestException && isObjectDescription)
  }

  private fun isMissingTableError(error: Throwable, tableName: String): Boolean {
    val msg = "${error.message.orEmpty()} ${error.cause?.message.orEmpty()}"
    return msg.contains(tableName, ignoreCase = true) && (
      msg.contains("PGRST205", ignoreCase = true) ||
        msg.contains("42P01", ignoreCase = true) ||
        msg.contains("schema cache", ignoreCase = true) ||
        msg.contains("does not exist", ignoreCase = true)
      )
  }

  private suspend fun verifyObjectDeleted(
    bucketName: String,
    path: String,
    bucket: BucketApi,
  ) {
    val stillExists = try {
      bucket.exists(path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (isAlreadyMissingStorageError(e)) {
        false
      } else {
        Log.e(TAG, "Failed verifying deletion of $path from $bucketName", e)
        throw IllegalStateException(
          "Failed to delete image files from cloud storage ($bucketName). Please try again.",
          e,
        )
      }
    }

    if (stillExists) {
      Log.e(TAG, "Storage object still exists after delete in $bucketName: $path")
      throw IllegalStateException(
        "Failed to delete image files from cloud storage ($bucketName): file could not be removed ($path). Please check storage permissions and try again."
      )
    }
  }

  suspend fun deleteFromBucket(
    bucketName: String,
    rawPaths: Iterable<String?>,
    bucketApi: BucketApi? = null,
  ) {
    val paths = normalizeStoragePaths(rawPaths, bucketName)
    if (paths.isEmpty()) return

    val bucket = bucketApi ?: SupabaseClientProvider.client.storage.from(bucketName)
    for (chunk in paths.chunked(100)) {
      var verifiedInFallback = false
      try {
        bucket.delete(chunk)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (chunk.size == 1 && isAlreadyMissingStorageError(e)) {
          Log.w(TAG, "Storage object already absent in $bucketName: ${chunk.first()}")
          verifiedInFallback = true
        } else if (chunk.size > 1 && isAlreadyMissingStorageError(e)) {
          verifiedInFallback = true
          for (singlePath in chunk) {
            var singleAlreadyMissing = false
            try {
              bucket.delete(listOf(singlePath))
            } catch (singleCancel: CancellationException) {
              throw singleCancel
            } catch (singleError: Exception) {
              if (isAlreadyMissingStorageError(singleError)) {
                Log.w(TAG, "Storage object already absent in $bucketName: $singlePath")
                singleAlreadyMissing = true
              } else {
                Log.e(TAG, "Failed deleting $singlePath from $bucketName", singleError)
                throw IllegalStateException(
                  "Failed to delete image files from cloud storage ($bucketName). Please try again.",
                  singleError,
                )
              }
            }
            if (!singleAlreadyMissing) {
              verifyObjectDeleted(bucketName, singlePath, bucket)
            }
          }
        } else {
          Log.e(TAG, "Failed deleting ${chunk.size} file(s) from $bucketName", e)
          throw IllegalStateException(
            "Failed to delete image files from cloud storage ($bucketName). Please try again.",
            e,
          )
        }
      }

      if (!verifiedInFallback) {
        for (path in chunk) {
          verifyObjectDeleted(bucketName, path, bucket)
        }
      }
    }
  }

  suspend fun collectQuestionPaperPathsForClasses(
    classRooms: List<ClassStorageProjection>,
    teacherId: String,
  ): List<String> {
    if (classRooms.isEmpty()) return emptyList()
    val directPaths = classRooms.mapNotNull { it.questionPaperPath }
    val classIds = classRooms.mapNotNull { it.id }.filter { it.isNotBlank() }.distinct()

    val multiPagePaths = if (classIds.isNotEmpty()) {
      try {
        SupabaseClientProvider.client.postgrest["class_question_paper_pages"]
          .select(Columns.list("storage_path")) {
            filter {
              isIn("class_id", classIds)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<StoragePathRow>()
          .mapNotNull { it.storagePath }
      } catch (e: Exception) {
        if (isMissingTableError(e, "class_question_paper_pages")) {
          emptyList()
        } else {
          throw e
        }
      }
    } else {
      emptyList()
    }

    return normalizeStoragePaths(directPaths + multiPagePaths, QUESTION_PAPERS_BUCKET)
  }

  suspend fun collectClassCleanupTarget(
    classIds: List<String>,
    teacherId: String,
  ): ClassCleanupTarget = coroutineScope {
    val validClassIds = classIds.filter { it.isNotBlank() }.distinct()
    if (validClassIds.isEmpty()) {
      return@coroutineScope ClassCleanupTarget(
        studentIds = emptyList(),
        paperIds = emptyList(),
        studentPaperPaths = emptyList(),
      )
    }

    val papersInClassesDeferred = async {
      SupabaseClientProvider.client.postgrest["student_papers"]
        .select(Columns.list("id")) {
          filter {
            isIn("class_id", validClassIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
    }

    val studentIdsDeferred = async {
      SupabaseClientProvider.client.postgrest["students"]
        .select(Columns.list("id")) {
          filter {
            isIn("class_id", validClassIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
        .distinct()
    }

    val papersInClasses = papersInClassesDeferred.await()
    val studentIds = studentIdsDeferred.await()

    val papersByStudents = if (studentIds.isNotEmpty()) {
      SupabaseClientProvider.client.postgrest["student_papers"]
        .select(Columns.list("id")) {
          filter {
            isIn("student_id", studentIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
    } else {
      emptyList()
    }

    val paperIds = (papersInClasses + papersByStudents).distinct()

    val pathsByPaperDeferred = async {
      if (paperIds.isNotEmpty()) {
        SupabaseClientProvider.client.postgrest["student_paper_pages"]
          .select(Columns.list("storage_path")) {
            filter {
              isIn("paper_id", paperIds)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<StoragePathRow>()
          .mapNotNull { it.storagePath }
      } else {
        emptyList()
      }
    }

    val pathsByStudentDeferred = async {
      if (studentIds.isNotEmpty()) {
        SupabaseClientProvider.client.postgrest["student_paper_pages"]
          .select(Columns.list("storage_path")) {
            filter {
              isIn("student_id", studentIds)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<StoragePathRow>()
          .mapNotNull { it.storagePath }
      } else {
        emptyList()
      }
    }

    val pathsByPaper = pathsByPaperDeferred.await()
    val pathsByStudent = pathsByStudentDeferred.await()

    ClassCleanupTarget(
      studentIds = studentIds,
      paperIds = paperIds,
      studentPaperPaths = normalizeStoragePaths(pathsByPaper + pathsByStudent, STUDENT_PAPERS_BUCKET),
    )
  }

  suspend fun collectStudentPaperPathsForClasses(
    classIds: List<String>,
    teacherId: String,
  ): List<String> = collectClassCleanupTarget(classIds, teacherId).studentPaperPaths

  suspend fun collectStudentCleanupTarget(
    studentId: String,
    teacherId: String,
  ): StudentCleanupTarget = coroutineScope {
    if (studentId.isBlank()) {
      return@coroutineScope StudentCleanupTarget(
        paperIds = emptyList(),
        storagePaths = emptyList(),
      )
    }

    val directPagePathsDeferred = async {
      SupabaseClientProvider.client.postgrest["student_paper_pages"]
        .select(Columns.list("storage_path")) {
          filter {
            eq("student_id", studentId)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<StoragePathRow>()
        .mapNotNull { it.storagePath }
    }

    val paperIdsDeferred = async {
      SupabaseClientProvider.client.postgrest["student_papers"]
        .select(Columns.list("id")) {
          filter {
            eq("student_id", studentId)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
        .distinct()
    }

    val directPagePaths = directPagePathsDeferred.await()
    val paperIds = paperIdsDeferred.await()

    val paperPagePaths = if (paperIds.isNotEmpty()) {
      SupabaseClientProvider.client.postgrest["student_paper_pages"]
        .select(Columns.list("storage_path")) {
          filter {
            isIn("paper_id", paperIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<StoragePathRow>()
        .mapNotNull { it.storagePath }
    } else {
      emptyList()
    }

    StudentCleanupTarget(
      paperIds = paperIds,
      storagePaths = normalizeStoragePaths(directPagePaths + paperPagePaths, STUDENT_PAPERS_BUCKET),
    )
  }

  suspend fun collectStudentPaperPathsForStudent(
    studentId: String,
    teacherId: String,
  ): List<String> = collectStudentCleanupTarget(studentId, teacherId).storagePaths

  suspend fun collectStudentPaperPathsForPaper(
    paperId: String,
    teacherId: String,
    inMemoryPaths: List<String>,
  ): List<String> {
    val dbPaths = if (paperId.isNotBlank()) {
      SupabaseClientProvider.client.postgrest["student_paper_pages"]
        .select(Columns.list("storage_path")) {
          filter {
            eq("paper_id", paperId)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<StoragePathRow>()
        .mapNotNull { it.storagePath }
    } else {
      emptyList()
    }
    return normalizeStoragePaths(inMemoryPaths + dbPaths, STUDENT_PAPERS_BUCKET)
  }

  suspend fun deleteEvaluationRecordsForPapers(
    paperIds: List<String>,
    teacherId: String,
  ) {
    val validPaperIds = paperIds.filter { it.isNotBlank() }.distinct()
    if (validPaperIds.isEmpty()) return

    val evalIds = try {
      SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
        .select(Columns.list("id")) {
          filter {
            isIn("paper_id", validPaperIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
        .distinct()
    } catch (e: Exception) {
      if (isMissingTableError(e, "student_paper_evaluations")) {
        emptyList()
      } else {
        throw e
      }
    }

    if (evalIds.isNotEmpty()) {
      try {
        SupabaseClientProvider.client.postgrest["student_paper_evaluation_questions"]
          .delete {
            filter {
              isIn("evaluation_id", evalIds)
            }
          }
      } catch (e: Exception) {
        if (!isMissingTableError(e, "student_paper_evaluation_questions")) {
          throw e
        }
      }
    }

    try {
      SupabaseClientProvider.client.postgrest["student_paper_evaluations"]
        .delete {
          filter {
            isIn("paper_id", validPaperIds)
            eq("teacher_id", teacherId)
          }
        }
    } catch (e: Exception) {
      if (!isMissingTableError(e, "student_paper_evaluations")) {
        throw e
      }
    }
  }

  suspend fun deletePaperChildRecords(
    paperIds: List<String>,
    teacherId: String,
  ) {
    val validPaperIds = paperIds.filter { it.isNotBlank() }.distinct()
    if (validPaperIds.isEmpty()) return

    deleteEvaluationRecordsForPapers(validPaperIds, teacherId)

    SupabaseClientProvider.client.postgrest["student_paper_pages"]
      .delete {
        filter {
          isIn("paper_id", validPaperIds)
          eq("teacher_id", teacherId)
        }
      }
  }

  suspend fun deleteStudentChildRecords(
    studentIds: List<String>,
    teacherId: String,
    precollectedPaperIds: List<String>? = null,
  ) {
    val validStudentIds = studentIds.filter { it.isNotBlank() }.distinct()
    if (validStudentIds.isEmpty()) return

    val paperIds = precollectedPaperIds
      ?.filter { it.isNotBlank() }
      ?.distinct()
      ?: SupabaseClientProvider.client.postgrest["student_papers"]
        .select(Columns.list("id")) {
          filter {
            isIn("student_id", validStudentIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
        .distinct()

    if (paperIds.isNotEmpty()) {
      deletePaperChildRecords(paperIds, teacherId)
    }

    SupabaseClientProvider.client.postgrest["student_paper_pages"]
      .delete {
        filter {
          isIn("student_id", validStudentIds)
          eq("teacher_id", teacherId)
        }
      }

    SupabaseClientProvider.client.postgrest["student_papers"]
      .delete {
        filter {
          isIn("student_id", validStudentIds)
          eq("teacher_id", teacherId)
        }
      }
  }

  suspend fun deleteClassChildRecords(
    classIds: List<String>,
    teacherId: String,
    precollectedStudentIds: List<String>? = null,
    precollectedPaperIds: List<String>? = null,
  ) {
    val validClassIds = classIds.filter { it.isNotBlank() }.distinct()
    if (validClassIds.isEmpty()) return

    val studentIds = precollectedStudentIds
      ?.filter { it.isNotBlank() }
      ?.distinct()
      ?: SupabaseClientProvider.client.postgrest["students"]
        .select(Columns.list("id")) {
          filter {
            isIn("class_id", validClassIds)
            eq("teacher_id", teacherId)
          }
        }
        .decodeList<IdRow>()
        .mapNotNull { it.id }
        .filter { it.isNotBlank() }
        .distinct()

    val paperIds = precollectedPaperIds
      ?.filter { it.isNotBlank() }
      ?.distinct()
      ?: run {
        val papersInClasses = SupabaseClientProvider.client.postgrest["student_papers"]
          .select(Columns.list("id")) {
            filter {
              isIn("class_id", validClassIds)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<IdRow>()
          .mapNotNull { it.id }
          .filter { it.isNotBlank() }

        val papersByStudents = if (studentIds.isNotEmpty()) {
          SupabaseClientProvider.client.postgrest["student_papers"]
            .select(Columns.list("id")) {
              filter {
                isIn("student_id", studentIds)
                eq("teacher_id", teacherId)
              }
            }
            .decodeList<IdRow>()
            .mapNotNull { it.id }
            .filter { it.isNotBlank() }
        } else {
          emptyList()
        }

        (papersInClasses + papersByStudents).distinct()
      }

    if (paperIds.isNotEmpty()) {
      deletePaperChildRecords(paperIds, teacherId)
    }

    if (studentIds.isNotEmpty()) {
      SupabaseClientProvider.client.postgrest["student_paper_pages"]
        .delete {
          filter {
            isIn("student_id", studentIds)
            eq("teacher_id", teacherId)
          }
        }

      SupabaseClientProvider.client.postgrest["student_papers"]
        .delete {
          filter {
            isIn("student_id", studentIds)
            eq("teacher_id", teacherId)
          }
        }
    }

    SupabaseClientProvider.client.postgrest["student_papers"]
      .delete {
        filter {
          isIn("class_id", validClassIds)
          eq("teacher_id", teacherId)
        }
      }

    SupabaseClientProvider.client.postgrest["students"]
      .delete {
        filter {
          isIn("class_id", validClassIds)
          eq("teacher_id", teacherId)
        }
      }

    try {
      SupabaseClientProvider.client.postgrest["class_question_paper_pages"]
        .delete {
          filter {
            isIn("class_id", validClassIds)
            eq("teacher_id", teacherId)
          }
        }
    } catch (e: Exception) {
      if (!isMissingTableError(e, "class_question_paper_pages")) {
        throw e
      }
    }
  }
}

interface PaperRepository {
  suspend fun getPapersByStudentId(studentId: String): Result<List<StudentPaper>>
  suspend fun createPaperWithPages(
    context: Context,
    studentId: String,
    classId: String,
    pageUris: List<Uri>,
    onProgress: (current: Int, total: Int) -> Unit,
  ): Result<StudentPaper>
  suspend fun deletePaper(paper: StudentPaper): Result<Unit>
  suspend fun deletePaperPage(page: StudentPaperPage): Result<Unit> =
    Result.failure(UnsupportedOperationException("deletePaperPage is not implemented"))
  suspend fun getSignedPageUrl(storagePath: String, expiresInSeconds: Long = 900): Result<String>
}

class SupabasePaperRepository(
  private val authRepository: AuthRepository,
) : PaperRepository {
  private val TAG = "PaperRepository"
  private val BUCKET_NAME = StorageCleanupHelper.STUDENT_PAPERS_BUCKET

  override suspend fun getPapersByStudentId(studentId: String): Result<List<StudentPaper>> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(
            IllegalStateException("Supabase is not configured yet.")
          )
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val papers = SupabaseClientProvider.client.postgrest["student_papers"]
          .select(Columns.raw("*, evaluation:student_paper_evaluations(status)")) {
            filter {
              eq("student_id", studentId)
              eq("teacher_id", teacherId)
            }
            order("created_at", order = Order.DESCENDING)
          }
          .decodeList<StudentPaper>()

        if (papers.isEmpty()) {
          return@withContext Result.success(emptyList())
        }

        val paperIds = papers.mapNotNull { it.id }
        val allPages = if (paperIds.isNotEmpty()) {
          SupabaseClientProvider.client.postgrest["student_paper_pages"]
            .select {
              filter {
                isIn("paper_id", paperIds)
                eq("teacher_id", teacherId)
              }
              order("page_number", order = Order.ASCENDING)
            }
            .decodeList<StudentPaperPage>()
        } else {
          emptyList()
        }

        val pagesByPaperId = allPages.groupBy { it.paperId }
        val populatedPapers = papers.map { paper ->
          paper.copy(pages = pagesByPaperId[paper.id] ?: emptyList())
        }

        Result.success(populatedPapers)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching papers for student $studentId", e)
        Result.failure(e)
      }
    }

  override suspend fun createPaperWithPages(
    context: Context,
    studentId: String,
    classId: String,
    pageUris: List<Uri>,
    onProgress: (current: Int, total: Int) -> Unit,
  ): Result<StudentPaper> = withContext(Dispatchers.IO) {
    if (pageUris.isEmpty()) {
      return@withContext Result.failure(IllegalArgumentException("Cannot create paper without any pages."))
    }

    if (!SupabaseClientProvider.isConfigured()) {
      return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
    }

    val teacherId = authRepository.getCurrentUserId()
      ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

    val paperTempId = UUID.randomUUID().toString()
    val uploadedPaths = mutableListOf<String>()
    val storage = SupabaseClientProvider.client.storage[BUCKET_NAME]

    try {
      // 1. Upload each page into private Supabase Storage
      for (index in pageUris.indices) {
        val uri = pageUris[index]
        val pageNum = index + 1
        onProgress(pageNum, pageUris.size)

        val imageBytes = readHighFidelityImageBytes(context, uri)
          ?: throw IllegalStateException("Could not read image data for page $pageNum")

        val path = "$teacherId/$classId/$studentId/$paperTempId/page_$pageNum.jpg"
        storage.upload(path, imageBytes) {
          upsert = true
        }
        uploadedPaths.add(path)
      }

      // 2. Insert header into student_papers table
      val paperRequest = CreatePaperRequest(
        studentId = studentId,
        classId = classId,
        teacherId = teacherId,
        totalPages = pageUris.size,
        status = "scanned",
      )

      val createdPaper = try {
        SupabaseClientProvider.client.postgrest["student_papers"]
          .insert(paperRequest) {
            select()
          }
          .decodeSingle<StudentPaper>()
      } catch (dbError: Exception) {
        // Rollback Storage upload on database failure
        Log.e(TAG, "Database insert into student_papers failed. Rolling back uploaded files.", dbError)
        try {
          if (uploadedPaths.isNotEmpty()) {
            storage.delete(uploadedPaths)
          }
        } catch (cleanupError: Exception) {
          Log.e(TAG, "Failed to clean up storage files during rollback", cleanupError)
        }
        throw dbError
      }

      val actualPaperId = createdPaper.id
        ?: throw IllegalStateException("Created paper has null ID")

      // 3. Insert rows into student_paper_pages table
      val pageRequests = uploadedPaths.mapIndexed { index, path ->
        CreatePaperPageRequest(
          paperId = actualPaperId,
          studentId = studentId,
          teacherId = teacherId,
          pageNumber = index + 1,
          storagePath = path,
        )
      }

      val createdPages = try {
        SupabaseClientProvider.client.postgrest["student_paper_pages"]
          .insert(pageRequests) {
            select()
          }
          .decodeList<StudentPaperPage>()
      } catch (pageDbError: Exception) {
        Log.e(TAG, "Database insert into student_paper_pages failed. Rolling back paper and files.", pageDbError)
        try {
          // Delete created student_paper row
          SupabaseClientProvider.client.postgrest["student_papers"].delete {
            filter { eq("id", actualPaperId) }
          }
          if (uploadedPaths.isNotEmpty()) {
            storage.delete(uploadedPaths)
          }
        } catch (cleanupError: Exception) {
          Log.e(TAG, "Failed rollback during page insertion failure", cleanupError)
        }
        throw pageDbError
      }

      Result.success(createdPaper.copy(pages = createdPages))
    } catch (e: Exception) {
      Log.e(TAG, "Error in createPaperWithPages", e)
      Result.failure(e)
    }
  }

  override suspend fun deletePaper(paper: StudentPaper): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val paperId = paper.id
          ?: return@withContext Result.failure(IllegalArgumentException("Cannot delete paper without ID"))

        // 1. Verify paper exists and belongs to the authenticated teacher before touching Storage
        val existingPaper = SupabaseClientProvider.client.postgrest["student_papers"]
          .select(Columns.list("id")) {
            filter {
              eq("id", paperId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<IdRow>()

        if (existingPaper.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Paper deletion failed. Paper was not found or permission denied.")
          )
        }

        // 2. Collect all page storage paths (from DB + in-memory paper) BEFORE deleting DB rows
        val storagePaths = StorageCleanupHelper.collectStudentPaperPathsForPaper(
          paperId = paperId,
          teacherId = teacherId,
          inMemoryPaths = paper.pages.map { it.storagePath },
        )

        // 3. Delete files from Supabase Storage FIRST. If Storage fails, abort so DB records remain intact for retry.
        StorageCleanupHelper.deleteFromBucket(
          bucketName = BUCKET_NAME,
          rawPaths = storagePaths,
        )

        // 4. Delete associated child records (evaluation questions, evaluations, pages) and parent paper record
        StorageCleanupHelper.deletePaperChildRecords(
          paperIds = listOf(paperId),
          teacherId = teacherId,
        )

        val deletedRows = SupabaseClientProvider.client.postgrest["student_papers"]
          .delete {
            select(Columns.list("id"))
            filter {
              eq("id", paperId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<IdRow>()

        if (deletedRows.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Paper deletion failed. Paper was not found or permission denied.")
          )
        }

        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Error deleting paper", e)
        Result.failure(e)
      }
    }

  override suspend fun deletePaperPage(page: StudentPaperPage): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        if (!SupabaseClientProvider.isConfigured()) {
          return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
        }

        val teacherId = authRepository.getCurrentUserId()
          ?: return@withContext Result.failure(IllegalStateException("Teacher is not authenticated."))

        val paperId = page.paperId
        if (paperId.isBlank()) {
          return@withContext Result.failure(IllegalArgumentException("Cannot delete page without paperId."))
        }

        // 1. Verify parent paper ownership before touching Storage
        val existingPapers = SupabaseClientProvider.client.postgrest["student_papers"]
          .select(Columns.list("id")) {
            filter {
              eq("id", paperId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<IdRow>()

        if (existingPapers.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Page deletion failed. Parent paper was not found or permission denied.")
          )
        }

        // 2. Fetch all pages for this paper owned by the authenticated teacher and locate the target page
        val allPaperPages = SupabaseClientProvider.client.postgrest["student_paper_pages"]
          .select {
            filter {
              eq("paper_id", paperId)
              eq("teacher_id", teacherId)
            }
            order("page_number", order = Order.ASCENDING)
          }
          .decodeList<StudentPaperPage>()

        val targetPage = if (!page.id.isNullOrBlank()) {
          allPaperPages.firstOrNull { it.id == page.id }
        } else {
          allPaperPages.firstOrNull { it.pageNumber == page.pageNumber }
        } ?: return@withContext Result.failure(
          IllegalStateException("Page deletion failed. Page was not found or permission denied.")
        )

        val targetPageId = targetPage.id
        if (targetPageId.isNullOrBlank()) {
          return@withContext Result.failure(
            IllegalStateException("Page deletion failed. Target page record is missing an ID.")
          )
        }

        val storagePaths = StorageCleanupHelper.normalizeStoragePaths(
          listOf(targetPage.storagePath, page.storagePath),
          BUCKET_NAME,
        )

        // 3. Delete only this page's file from Supabase Storage FIRST.
        // No database records have been mutated yet, so if Storage fails, all DB state remains intact.
        StorageCleanupHelper.deleteFromBucket(
          bucketName = BUCKET_NAME,
          rawPaths = storagePaths,
        )

        // 4. Delete DB record(s), clear outdated evaluations, and keep page_number (1..N) & total_pages consistent
        if (allPaperPages.size <= 1) {
          // Last page of the paper: remove child records and the parent paper record
          StorageCleanupHelper.deletePaperChildRecords(
            paperIds = listOf(paperId),
            teacherId = teacherId,
          )

          val deletedPapers = SupabaseClientProvider.client.postgrest["student_papers"]
            .delete {
              select(Columns.list("id"))
              filter {
                eq("id", paperId)
                eq("teacher_id", teacherId)
              }
            }
            .decodeList<IdRow>()

          if (deletedPapers.isEmpty()) {
            return@withContext Result.failure(
              IllegalStateException("Page deletion failed. Parent paper was not found or permission denied.")
            )
          }
        } else {
          val deletedPages = SupabaseClientProvider.client.postgrest["student_paper_pages"]
            .delete {
              select(Columns.list("id"))
              filter {
                eq("id", targetPageId)
                eq("paper_id", paperId)
                eq("teacher_id", teacherId)
              }
            }
            .decodeList<IdRow>()

          if (deletedPages.isEmpty()) {
            return@withContext Result.failure(
              IllegalStateException("Page deletion failed. Page was not found or permission denied.")
            )
          }

          // Clear any existing evaluation for this paper since paper content has changed
          StorageCleanupHelper.deleteEvaluationRecordsForPapers(
            paperIds = listOf(paperId),
            teacherId = teacherId,
          )

          val remainingPages = allPaperPages
            .filterNot { it.id == targetPageId }
            .sortedBy { it.pageNumber }

          // Resequence remaining pages to continuous 1..N as required by process-and-grade-paper
          remainingPages.forEachIndexed { index, remainingPage ->
            val expectedPageNumber = index + 1
            val remainingPageId = remainingPage.id
            if (remainingPage.pageNumber != expectedPageNumber && !remainingPageId.isNullOrBlank()) {
              val updatedPageRows = SupabaseClientProvider.client.postgrest["student_paper_pages"]
                .update(
                  buildJsonObject {
                    put("page_number", expectedPageNumber)
                  }
                ) {
                  select(Columns.list("id"))
                  filter {
                    eq("id", remainingPageId)
                    eq("paper_id", paperId)
                    eq("teacher_id", teacherId)
                  }
                }
                .decodeList<IdRow>()

              if (updatedPageRows.isEmpty()) {
                return@withContext Result.failure(
                  IllegalStateException("Failed to resequence remaining paper pages after deletion.")
                )
              }
            }
          }

          val newTotalPages = remainingPages.size.coerceAtLeast(1)
          val updatedPapers = SupabaseClientProvider.client.postgrest["student_papers"]
            .update(
              buildJsonObject {
                put("total_pages", newTotalPages)
                put("status", "scanned")
                put("ocr_status", "pending")
                put("combined_ocr_text", JsonNull)
                put("ocr_completed_at", JsonNull)
                put("ocr_error", JsonNull)
              }
            ) {
              select(Columns.list("id"))
              filter {
                eq("id", paperId)
                eq("teacher_id", teacherId)
              }
            }
            .decodeList<IdRow>()

          if (updatedPapers.isEmpty()) {
            return@withContext Result.failure(
              IllegalStateException("Failed to update total_pages on student paper.")
            )
          }
        }

        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Error deleting paper page", e)
        Result.failure(e)
      }
    }

  override suspend fun getSignedPageUrl(
    storagePath: String,
    expiresInSeconds: Long,
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(IllegalStateException("Supabase is not configured yet."))
      }

      val signedUrl = SupabaseClientProvider.client.storage[BUCKET_NAME]
        .createSignedUrl(path = storagePath, expiresIn = expiresInSeconds.seconds)

      Result.success(signedUrl)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error generating signed URL for $storagePath", e)
      Result.failure(e)
    }
  }

  /**
   * Prepares high-fidelity image bytes preserving handwriting clarity for future OCR.
   * Only scales down if the image exceeds 2560px to prevent OutOfMemory errors while keeping fine strokes sharp.
   */
  private fun readHighFidelityImageBytes(context: Context, uri: Uri): ByteArray? =
    readHighFidelityImageBytesInternal(context, uri)
}

internal fun readHighFidelityImageBytesInternal(context: Context, uri: Uri): ByteArray? {
  return try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, options)
    }

    val maxDimension = 2560
    val srcWidth = options.outWidth
    val srcHeight = options.outHeight
    if (srcWidth <= 0 || srcHeight <= 0) {
      return null
    }

    var inSampleSize = 1
    if (srcWidth > maxDimension || srcHeight > maxDimension) {
      val halfHeight = srcHeight / 2
      val halfWidth = srcWidth / 2
      while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
        inSampleSize *= 2
      }
    }

    val decodeOptions = BitmapFactory.Options().apply {
      this.inSampleSize = inSampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: return null

    try {
      val outputStream = ByteArrayOutputStream()
      // Quality 92 preserves handwriting edges and pen stroke clarity without aggressive loss
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
      outputStream.toByteArray()
    } finally {
      bitmap.recycle()
    }
  } catch (e: Exception) {
    Log.e("PaperRepository", "Failed reading image bytes from Uri: $uri", e)
    null
  }
}

/**
 * Safely deletes an app-owned temporary camera capture file located inside the app's cache directory.
 * Never deletes gallery/media URIs or files outside the app's cache directory.
 */
internal fun deleteAppOwnedTempCameraFile(
  context: Context?,
  uri: Uri?,
  fallbackCacheDir: File? = null,
): Boolean {
  if (uri == null) return false
  return try {
    val baseCacheDir = context?.cacheDir
      ?: fallbackCacheDir
      ?: System.getProperty("java.io.tmpdir")?.let(::File)
      ?: return false
    val cacheRoot = baseCacheDir.canonicalFile

    val candidateFile: File? = when (uri.scheme) {
      "file" -> uri.path?.let(::File)
      "content" -> {
        val authority = uri.authority.orEmpty()
        val isAppFileProvider = if (context != null) {
          authority == "${context.packageName}.fileprovider"
        } else {
          authority.endsWith(".fileprovider")
        }
        if (isAppFileProvider) {
          val rawPath = uri.path.orEmpty().removePrefix("/")
          val relativePath = when {
            rawPath.startsWith("camera_photos/") -> rawPath.removePrefix("camera_photos/")
            else -> rawPath
          }
          if (relativePath.isNotBlank()) File(cacheRoot, relativePath) else null
        } else {
          null
        }
      }
      else -> null
    }

    if (candidateFile == null) return false
    val canonicalTarget = candidateFile.canonicalFile
    val isInsideCacheDir = canonicalTarget.path.startsWith(cacheRoot.path + File.separator)
    val fileName = canonicalTarget.name
    val isAppTempCameraFile =
      fileName.startsWith("answer_page_") ||
        fileName.startsWith("student_list_") ||
        fileName.startsWith("qp_capture_")

    if (isInsideCacheDir && isAppTempCameraFile && canonicalTarget.exists() && canonicalTarget.isFile) {
      canonicalTarget.delete()
    } else {
      false
    }
  } catch (_: Exception) {
    false
  }
}

