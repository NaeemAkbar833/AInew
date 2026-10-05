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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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
  suspend fun getSignedPageUrl(storagePath: String, expiresInSeconds: Long = 900): Result<String>
}

class SupabasePaperRepository(
  private val authRepository: AuthRepository,
) : PaperRepository {
  private val TAG = "PaperRepository"
  private val BUCKET_NAME = "student-papers"

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
          .select {
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

        // 1. Delete database record with verification
        val deletedRows = SupabaseClientProvider.client.postgrest["student_papers"]
          .delete {
            select()
            filter {
              eq("id", paperId)
              eq("teacher_id", teacherId)
            }
          }
          .decodeList<StudentPaper>()

        if (deletedRows.isEmpty()) {
          return@withContext Result.failure(
            IllegalStateException("Paper deletion failed. Paper was not found or permission denied.")
          )
        }

        // 2. Explicitly delete files from private Storage (not relying solely on cascade)
        val storagePaths = paper.pages.map { it.storagePath }
        if (storagePaths.isNotEmpty()) {
          try {
            SupabaseClientProvider.client.storage[BUCKET_NAME].delete(storagePaths)
          } catch (storageError: Exception) {
            Log.w(TAG, "Storage deletion failed during paper delete: ${storageError.message}")
          }
        }

        Result.success(Unit)
      } catch (e: Exception) {
        Log.e(TAG, "Error deleting paper", e)
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
    } catch (e: Exception) {
      Log.e(TAG, "Error generating signed URL for $storagePath", e)
      Result.failure(e)
    }
  }

  /**
   * Prepares high-fidelity image bytes preserving handwriting clarity for future OCR.
   * Only scales down if the image exceeds 2560px to prevent OutOfMemory errors while keeping fine strokes sharp.
   */
  private fun readHighFidelityImageBytes(context: Context, uri: Uri): ByteArray? {
    return try {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      }

      val maxDimension = 2560
      val srcWidth = options.outWidth
      val srcHeight = options.outHeight

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

      val outputStream = ByteArrayOutputStream()
      // Quality 92 preserves handwriting edges and pen stroke clarity without aggressive loss
      bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
      outputStream.toByteArray()
    } catch (e: Exception) {
      Log.e(TAG, "Failed reading image bytes from Uri: $uri", e)
      null
    }
  }
}
