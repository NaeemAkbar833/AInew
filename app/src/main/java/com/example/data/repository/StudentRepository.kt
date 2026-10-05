package com.example.data.repository

import android.util.Log
import com.example.data.model.CreateStudentRequest
import com.example.data.model.Student
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}
