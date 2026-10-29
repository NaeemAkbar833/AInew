package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.model.EvaluationDetail
import com.example.data.model.EvaluationDbRecord
import com.example.data.model.EvaluationQuestion
import com.example.data.model.EvaluationStatus
import com.example.data.model.Exam
import com.example.data.model.ExamType
import com.example.data.model.QuestionDbRecord
import com.example.data.model.Student
import com.example.data.model.ExtractedStudent
import com.example.data.model.StudentPaper
import com.example.data.model.StudentPaperPage
import com.example.data.model.MarkEditValidationResult
import com.example.data.model.calculateCountedEvaluationTotalMarks
import com.example.data.model.calculateEffectiveEvaluationQuestionTotalMarks
import com.example.data.model.calculateEffectiveQuestionTotalMarks
import com.example.data.model.compareQuestionNumbers
import com.example.data.model.parseAiEvaluationResponse
import com.example.data.model.sortEvaluationQuestionsNumerically
import com.example.data.model.validatePaperEvaluationForSave
import com.example.data.model.validateQuestionMarkEdit
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.OcrRepository
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseOcrRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.ui.auth.AuthViewModel
import com.example.ui.classes.ClassViewModel
import com.example.ui.exam.ExamViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.paper.ScanPaperViewModel
import com.example.ui.paper.StudentDetailViewModel
import com.example.ui.results.ClassReportViewModel
import com.example.ui.results.ClassResultsViewModel
import com.example.ui.results.StudentResultDetailViewModel
import com.example.ui.student.StudentViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeAuthRepository(
  private var signedInUser: String? = null,
  var shouldSucceed: Boolean = true,
  var customUserId: String? = null,
  var shouldFailSignOut: Boolean = false,
  var throwSignOutCancellation: Boolean = false,
) : AuthRepository {
  override suspend fun signIn(email: String, password: String): Result<String> {
    return if (shouldSucceed) {
      signedInUser = email
      Result.success(email)
    } else {
      Result.failure(Exception("Invalid credentials"))
    }
  }

  override suspend fun signUp(email: String, password: String): Result<String> {
    return if (shouldSucceed) {
      signedInUser = email
      Result.success(email)
    } else {
      Result.failure(Exception("Registration failed"))
    }
  }

  override suspend fun signOut(): Result<Unit> {
    if (throwSignOutCancellation) {
      throw kotlinx.coroutines.CancellationException("Sign out cancelled")
    }
    if (shouldFailSignOut) {
      return Result.failure(Exception("Sign out network error"))
    }
    signedInUser = null
    return Result.success(Unit)
  }

  override fun getCurrentUserEmail(): String? = signedInUser

  override fun getCurrentUserId(): String? = if (signedInUser != null) (customUserId ?: "user-123") else null

  override fun isUserSignedIn(): Boolean = signedInUser != null

  override suspend fun checkSession(): Boolean = signedInUser != null
}

internal class FakeExamRepository(
  initialExams: List<Exam> = emptyList(),
  var shouldFail: Boolean = false,
  var throwCancellation: Boolean = false,
  var swallowCancellationIntoFailure: Boolean = false,
  var deferredExamsResult: kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>? = null,
  var deferredExamByIdResult: kotlinx.coroutines.CompletableDeferred<Result<Exam?>>? = null,
  var onBeforeGetExams: (() -> Unit)? = null,
) : ExamRepository {
  private val exams = initialExams.toMutableList()
  val deletedExamIds = mutableListOf<String>()
  var getExamsCallCount: Int = 0
  var getExamByIdCallCount: Int = 0
  var cancelledRequestCount: Int = 0

  override suspend fun getExams(): Result<List<Exam>> {
    getExamsCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated coroutine cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    onBeforeGetExams?.invoke()
    val deferred = deferredExamsResult
    if (deferred != null) {
      return try {
        deferred.await()
      } catch (e: kotlinx.coroutines.CancellationException) {
        cancelledRequestCount++
        if (swallowCancellationIntoFailure) {
          Result.failure(e)
        } else {
          throw e
        }
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch exams"))
    } else {
      Result.success(exams.toList())
    }
  }

  override suspend fun getExamById(id: String): Result<Exam?> {
    getExamByIdCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated exam by id cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val deferred = deferredExamByIdResult
    if (deferred != null) {
      return deferred.await()
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch exam"))
    } else {
      Result.success(exams.firstOrNull { it.id == id })
    }
  }

  override suspend fun createExam(name: String, type: ExamType, date: String): Result<Exam> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to create exam"))
    } else {
      val exam = Exam(
        id = "exam-${exams.size + 1}",
        teacherId = "user-123",
        name = name,
        type = type.displayName,
        date = date,
      )
      exams.add(0, exam)
      Result.success(exam)
    }
  }

  override suspend fun deleteExam(id: String): Result<Unit> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to delete exam"))
    }
    val removed = exams.removeAll { it.id == id }
    if (!removed) {
      return Result.failure(IllegalStateException("Deletion failed. The exam was not found or access was denied."))
    }
    deletedExamIds.add(id)
    return Result.success(Unit)
  }
}

internal class FakeClassRepository(
  initialClasses: List<ClassRoom> = emptyList(),
  var shouldFail: Boolean = false,
  var throwCancellation: Boolean = false,
  var swallowCancellationIntoFailure: Boolean = false,
  var ignoreCancellationOnDeferred: Boolean = false,
  var deferredClassesResult: kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>? = null,
  var deferredClassByIdResult: kotlinx.coroutines.CompletableDeferred<Result<ClassRoom?>>? = null,
) : ClassRepository {
  private val classes = initialClasses.toMutableList()
  val deletedClassIds = mutableListOf<String>()
  var getClassesCallCount: Int = 0
  var getClassByIdCallCount: Int = 0
  var getClassCountCallCount: Int = 0
  var deferredClassCountResult: kotlinx.coroutines.CompletableDeferred<Result<Int>>? = null
  val deferredClassesByExamId = mutableMapOf<String, kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>>()

  override suspend fun getClassCountForTeacher(): Result<Int> {
    getClassCountCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated class count cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val countDeferred = deferredClassCountResult
    if (countDeferred != null) {
      return countDeferred.await()
    }
    val classesDeferred = deferredClassesResult
    if (classesDeferred != null) {
      return try {
        val res = classesDeferred.await()
        res.map { it.size }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch class count"))
    } else {
      Result.success(classes.size)
    }
  }

  override suspend fun getClassesByExamId(examId: String): Result<List<ClassRoom>> {
    getClassesCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated getClasses cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val examDeferred = deferredClassesByExamId[examId]
    if (examDeferred != null) {
      return if (ignoreCancellationOnDeferred) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          examDeferred.await()
        }
      } else {
        examDeferred.await()
      }
    }
    val deferred = deferredClassesResult
    if (deferred != null) {
      return if (ignoreCancellationOnDeferred) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          deferred.await()
        }
      } else {
        deferred.await()
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch classes"))
    } else {
      Result.success(classes.filter { it.examId == examId })
    }
  }

  override suspend fun getClassById(classId: String): Result<ClassRoom?> {
    getClassByIdCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated class by id cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val deferred = deferredClassByIdResult
    if (deferred != null) {
      return if (ignoreCancellationOnDeferred) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          deferred.await()
        }
      } else {
        deferred.await()
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch class"))
    } else {
      Result.success(classes.firstOrNull { it.id == classId })
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
  ): Result<ClassRoom> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to create class"))
    } else {
      val path = if (questionPaperBytes != null) "user-123/$examId/paper.jpg" else null
      val newClass = ClassRoom(
        id = "class-${classes.size + 1}",
        examId = examId,
        teacherId = "user-123",
        session = session,
        semester = semester,
        timing = timing.displayName,
        subjectName = subjectName,
        totalMarks = totalMarks,
        questionPaperPath = path,
      )
      classes.add(newClass)
      Result.success(newClass)
    }
  }

  override suspend fun deleteClass(id: String): Result<Unit> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to delete class"))
    }
    val removed = classes.removeAll { it.id == id }
    if (!removed) {
      return Result.failure(IllegalStateException("Deletion failed. The class was not found or access was denied."))
    }
    deletedClassIds.add(id)
    return Result.success(Unit)
  }
}

internal class FakeStudentRepository(
  initialStudents: List<Student> = emptyList(),
  var shouldFail: Boolean = false,
  var throwCancellation: Boolean = false,
  var swallowCancellationIntoFailure: Boolean = false,
  var ignoreCancellationOnDeferred: Boolean = false,
  var deferredStudentsByClassIdResult: kotlinx.coroutines.CompletableDeferred<Result<List<Student>>>? = null,
  var deferredStudentByIdResult: kotlinx.coroutines.CompletableDeferred<Result<Student?>>? = null,
) : StudentRepository {
  private val students = initialStudents.toMutableList()
  val deletedStudentIds = mutableListOf<String>()
  var getStudentsByClassIdCallCount: Int = 0
  var getStudentByIdCallCount: Int = 0
  var cancelledRequestCount: Int = 0

  override suspend fun getStudentsByClassId(classId: String): Result<List<Student>> {
    getStudentsByClassIdCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated student list cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val deferred = deferredStudentsByClassIdResult
    if (deferred != null) {
      return try {
        if (ignoreCancellationOnDeferred) {
          kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            deferred.await()
          }
        } else {
          deferred.await()
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        cancelledRequestCount++
        if (swallowCancellationIntoFailure) {
          Result.failure(e)
        } else {
          throw e
        }
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch students"))
    } else {
      Result.success(students.filter { it.classId == classId }.sortedBy { it.rollNumber })
    }
  }

  override suspend fun getStudentById(studentId: String): Result<Student?> {
    getStudentByIdCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated student by id cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val deferred = deferredStudentByIdResult
    if (deferred != null) {
      return if (ignoreCancellationOnDeferred) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          deferred.await()
        }
      } else {
        deferred.await()
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch student"))
    } else {
      Result.success(students.firstOrNull { it.id == studentId })
    }
  }

  override suspend fun addStudent(
    classId: String,
    name: String,
    fatherName: String,
    rollNumber: String,
  ): Result<Student> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to add student"))
    } else {
      // Enforce unique roll number within the same class
      if (students.any { it.classId == classId && it.rollNumber.equals(rollNumber.trim(), ignoreCase = true) }) {
        return Result.failure(IllegalStateException("A student with roll number '$rollNumber' already exists in this class."))
      }
      val newStudent = Student(
        id = "student-${students.size + 1}",
        classId = classId,
        teacherId = "user-123",
        name = name.trim(),
        fatherName = fatherName.trim(),
        rollNumber = rollNumber.trim(),
      )
      students.add(newStudent)
      Result.success(newStudent)
    }
  }

  override suspend fun deleteStudent(studentId: String): Result<Unit> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to delete student"))
    }
    val removed = students.removeAll { it.id == studentId }
    return if (removed) {
      deletedStudentIds.add(studentId)
      Result.success(Unit)
    } else {
      Result.failure(IllegalStateException("Student not found"))
    }
  }

  override suspend fun extractStudentList(classId: String, images: List<String>): Result<List<ExtractedStudent>> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to extract students"))
    } else {
      Result.success(emptyList())
    }
  }

  override suspend fun addStudentsBatch(classId: String, students: List<ExtractedStudent>): Result<Int> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to add students batch"))
    } else {
      students.forEach { s ->
        this.students.add(
          Student(
            id = "student-${this.students.size + 1}",
            classId = classId,
            teacherId = "user-123",
            name = s.name.trim(),
            fatherName = s.fatherName.trim(),
            rollNumber = s.rollNumber.trim(),
          )
        )
      }
      Result.success(students.size)
    }
  }
}

internal class FakePaperRepository(
  initialPapers: List<StudentPaper> = emptyList(),
  var shouldFail: Boolean = false,
  var throwCancellation: Boolean = false,
  var swallowCancellationIntoFailure: Boolean = false,
  var ignoreCancellationOnDeferred: Boolean = false,
  var ignoreSignedUrlCancellation: Boolean = false,
  var deferredPapersResult: kotlinx.coroutines.CompletableDeferred<Result<List<StudentPaper>>>? = null,
  var deferredSignedUrlResult: kotlinx.coroutines.CompletableDeferred<Result<String>>? = null,
) : PaperRepository {
  private val papers = initialPapers.toMutableList()
  val deletedStoragePaths = mutableListOf<String>()
  var lastUploadedPageUris: List<Uri> = emptyList()
  var getPapersCallCount: Int = 0
  var getSignedPageUrlCallCount: Int = 0
  var cancelledRequestCount: Int = 0

  override suspend fun getPapersByStudentId(studentId: String): Result<List<StudentPaper>> {
    getPapersCallCount++
    if (throwCancellation) {
      val ce = kotlinx.coroutines.CancellationException("Simulated papers load cancellation")
      if (swallowCancellationIntoFailure) {
        return Result.failure(ce)
      }
      throw ce
    }
    val deferred = deferredPapersResult
    if (deferred != null) {
      return try {
        if (ignoreCancellationOnDeferred) {
          kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            deferred.await()
          }
        } else {
          deferred.await()
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        cancelledRequestCount++
        if (swallowCancellationIntoFailure) {
          Result.failure(e)
        } else {
          throw e
        }
      }
    }
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch papers"))
    } else {
      Result.success(papers.filter { it.studentId == studentId })
    }
  }

  override suspend fun createPaperWithPages(
    context: Context,
    studentId: String,
    classId: String,
    pageUris: List<Uri>,
    onProgress: (current: Int, total: Int) -> Unit,
  ): Result<StudentPaper> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to upload paper"))
    } else {
      lastUploadedPageUris = pageUris.toList()
      val paperId = "paper-${papers.size + 1}"
      val pages = pageUris.mapIndexed { idx, _ ->
        onProgress(idx + 1, pageUris.size)
        StudentPaperPage(
          id = "page-${idx + 1}",
          paperId = paperId,
          studentId = studentId,
          teacherId = "user-123",
          pageNumber = idx + 1,
          storagePath = "user-123/$classId/$studentId/$paperId/page_${idx + 1}.jpg",
        )
      }
      val newPaper = StudentPaper(
        id = paperId,
        studentId = studentId,
        classId = classId,
        teacherId = "user-123",
        totalPages = pageUris.size,
        status = "scanned",
        createdAt = "2026-10-05T00:00:00Z",
        pages = pages,
      )
      papers.add(0, newPaper)
      Result.success(newPaper)
    }
  }

  override suspend fun deletePaper(paper: StudentPaper): Result<Unit> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to delete paper"))
    }
    return try {
      val paperId = paper.id ?: return Result.failure(IllegalArgumentException("Cannot delete paper without ID"))
      val storedPaper = papers.firstOrNull { it.id == paperId }
        ?: return Result.failure(IllegalStateException("Paper deletion failed. Paper was not found or permission denied."))

      papers.removeAll { it.id == paperId }
      deletedStoragePaths.addAll(storedPaper.pages.map { it.storagePath })
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deletePaperPage(page: StudentPaperPage): Result<Unit> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to delete page"))
    }
    return try {
      val idx = papers.indexOfFirst { it.id == page.paperId }
      if (idx == -1) {
        return Result.failure(IllegalStateException("Paper not found"))
      }
      val paper = papers[idx]
      val remaining = paper.pages
        .filterNot { it.id == page.id }
        .sortedBy { it.pageNumber }
        .mapIndexed { index, remainingPage ->
          remainingPage.copy(pageNumber = index + 1)
        }
      deletedStoragePaths.add(page.storagePath)
      if (remaining.isEmpty()) {
        papers.removeAt(idx)
      } else {
        papers[idx] = paper.copy(
          totalPages = remaining.size,
          status = "scanned",
          ocrStatus = "pending",
          combinedOcrText = null,
          ocrError = null,
          evaluation = null,
          pages = remaining,
        )
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun getSignedPageUrl(storagePath: String, expiresInSeconds: Long): Result<String> {
    getSignedPageUrlCallCount++
    val deferred = deferredSignedUrlResult
    if (deferred != null) {
      return if (ignoreSignedUrlCancellation) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          deferred.await()
        }
      } else {
        deferred.await()
      }
    }
    return Result.success("https://signed.url/$storagePath")
  }
}

class FakeOcrRepository(
  var shouldSucceed: Boolean = true,
  var cannedOcrText: String = "1. Answer: Database normalization reduces redundancy.",
  var errorMessage: String = "OCR provider timed out.",
) : OcrRepository {
  var lastPaperId: String? = null
  var lastPageId: String? = null

  override suspend fun processPageOcr(paperId: String, pageId: String): Result<String> {
    lastPaperId = paperId
    lastPageId = pageId
    return if (shouldSucceed) {
      Result.success(cannedOcrText)
    } else {
      Result.failure(Exception(errorMessage))
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `verify app name resource is GradeScan`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GradeScan", appName)
  }

  @Test
  fun `verify AuthViewModel input validations and login failure`() = runTest {
    val fakeRepo = FakeAuthRepository(shouldSucceed = false)
    val viewModel = AuthViewModel(fakeRepo)

    viewModel.login(onSuccess = {})
    assertEquals("Please enter your email address.", viewModel.uiState.value.errorMessage)

    viewModel.onEmailChanged("not-an-email")
    viewModel.onPasswordChanged("password123")
    viewModel.login(onSuccess = {})
    assertEquals("Please enter a valid email address.", viewModel.uiState.value.errorMessage)

    viewModel.onEmailChanged("teacher@school.edu")
    viewModel.login(onSuccess = {})
    assertEquals("Invalid credentials", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `verify AuthViewModel signUp validation and success`() = runTest {
    val fakeRepo = FakeAuthRepository(shouldSucceed = true)
    val viewModel = AuthViewModel(fakeRepo)

    viewModel.onEmailChanged("teacher@school.edu")
    viewModel.onPasswordChanged("short")
    viewModel.onConfirmPasswordChanged("short")
    viewModel.signUp(onSuccess = {})
    assertEquals("Password must be at least 6 characters.", viewModel.uiState.value.errorMessage)

    viewModel.onPasswordChanged("secret123")
    viewModel.onConfirmPasswordChanged("different123")
    viewModel.signUp(onSuccess = {})
    assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)

    var successCalled = false
    viewModel.onConfirmPasswordChanged("secret123")
    viewModel.signUp(onSuccess = { successCalled = true })

    assertTrue(successCalled)
    assertTrue(viewModel.uiState.value.isSuccess)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `verify HomeViewModel quick action placeholders and logout flow`() = runTest {
    val fakeRepo = FakeAuthRepository(signedInUser = "teacher@school.edu")
    val homeViewModel = HomeViewModel(fakeRepo)

    assertEquals("teacher@school.edu", homeViewModel.uiState.value.teacherEmail)

    homeViewModel.onQuickActionClick("Classes")
    assertEquals(
      "Classes is scheduled for a subsequent phase.",
      homeViewModel.uiState.value.placeholderMessage
    )

    homeViewModel.onQuickActionClick("Results")
    assertEquals(
      "Results is scheduled for a subsequent phase.",
      homeViewModel.uiState.value.placeholderMessage
    )

    homeViewModel.dismissPlaceholderMessage()
    assertNull(homeViewModel.uiState.value.placeholderMessage)

    var signedOutCalled = false
    homeViewModel.signOut { signedOutCalled = true }

    assertTrue(signedOutCalled)
    assertFalse(fakeRepo.isUserSignedIn())
  }

  @Test
  fun `verify ExamViewModel loadExams and createExam flow`() = runTest {
    val fakeExamRepo = FakeExamRepository()
    val examViewModel = ExamViewModel(fakeExamRepo)

    assertTrue(examViewModel.uiState.value.exams.isEmpty())

    examViewModel.createExam(name = "", type = ExamType.MID_TERM, date = "2026-10-05", onSuccess = {})
    assertEquals("Exam name cannot be empty.", examViewModel.uiState.value.createErrorMessage)

    examViewModel.createExam(name = "Mathematics Mid Term", type = ExamType.MID_TERM, date = "", onSuccess = {})
    assertEquals("Exam date is required.", examViewModel.uiState.value.createErrorMessage)

    var createdExam: Exam? = null
    examViewModel.createExam(
      name = "Mathematics Mid Term",
      type = ExamType.MID_TERM,
      date = "2026-10-05",
      onSuccess = { createdExam = it }
    )

    assertNotNull(createdExam)
    assertEquals("Mathematics Mid Term", createdExam?.name)
    assertEquals("Mid Term", createdExam?.type)
    assertEquals("2026-10-05", createdExam?.date)
    assertEquals(1, examViewModel.uiState.value.exams.size)

    examViewModel.loadExamDetail("exam-1")
    assertEquals("Mathematics Mid Term", examViewModel.uiState.value.selectedExam?.name)
  }

  @Test
  fun `verify ExamViewModel deleteExam flow`() = runTest {
    val exam1 = Exam(id = "exam-1", teacherId = "user-123", name = "Calculus", type = "Mid Term", date = "2026-10-01")
    val exam2 = Exam(id = "exam-2", teacherId = "user-123", name = "Physics", type = "Final", date = "2026-10-02")
    val fakeExamRepo = FakeExamRepository(initialExams = listOf(exam1, exam2))
    val examViewModel = ExamViewModel(fakeExamRepo)

    assertEquals(2, examViewModel.uiState.value.exams.size)

    var deleteSuccessCalled = false
    examViewModel.deleteExam("exam-1") {
      deleteSuccessCalled = true
    }

    assertTrue(deleteSuccessCalled)
    assertEquals(1, examViewModel.uiState.value.exams.size)
    assertEquals("Physics", examViewModel.uiState.value.exams.first().name)
    assertEquals("Exam deleted successfully.", examViewModel.uiState.value.feedbackMessage)

    // Verify failure case
    fakeExamRepo.shouldFail = true
    examViewModel.deleteExam("exam-2")
    assertEquals(1, examViewModel.uiState.value.exams.size)
    assertEquals("Failed to delete exam", examViewModel.uiState.value.deleteErrorMessage)
  }

  @Test
  fun `verify ClassViewModel validations and createClass flow`() = runTest {
    val fakeClassRepo = FakeClassRepository()
    val classViewModel = ClassViewModel(fakeClassRepo)

    // Validation checks
    classViewModel.createClass(
      examId = "exam-1",
      session = "",
      semester = "4th",
      timing = ClassTiming.MORNING,
      subjectName = "Data Structures",
      totalMarksText = "100",
      questionPaperBytes = null,
      fileExtension = null,
      onSuccess = {}
    )
    assertEquals("Class Session is required (e.g. 2022-2026).", classViewModel.uiState.value.saveErrorMessage)

    classViewModel.createClass(
      examId = "exam-1",
      session = "2022-2026",
      semester = "",
      timing = ClassTiming.MORNING,
      subjectName = "Data Structures",
      totalMarksText = "100",
      questionPaperBytes = null,
      fileExtension = null,
      onSuccess = {}
    )
    assertEquals("Semester is required (e.g. 4th).", classViewModel.uiState.value.saveErrorMessage)

    classViewModel.createClass(
      examId = "exam-1",
      session = "2022-2026",
      semester = "4th",
      timing = ClassTiming.MORNING,
      subjectName = "",
      totalMarksText = "100",
      questionPaperBytes = null,
      fileExtension = null,
      onSuccess = {}
    )
    assertEquals("Subject Name is required.", classViewModel.uiState.value.saveErrorMessage)

    classViewModel.createClass(
      examId = "exam-1",
      session = "2022-2026",
      semester = "4th",
      timing = ClassTiming.MORNING,
      subjectName = "Data Structures",
      totalMarksText = "invalid",
      questionPaperBytes = null,
      fileExtension = null,
      onSuccess = {}
    )
    assertEquals("Total Marks must be a positive number.", classViewModel.uiState.value.saveErrorMessage)

    // Successful Class 1 Creation (with question paper)
    var createdClass1: ClassRoom? = null
    classViewModel.createClass(
      examId = "exam-1",
      session = "2022-2026",
      semester = "4th",
      timing = ClassTiming.MORNING,
      subjectName = "Data Structures",
      totalMarksText = "100",
      questionPaperBytes = byteArrayOf(1, 2, 3),
      fileExtension = "jpg",
      onSuccess = { createdClass1 = it }
    )

    assertNotNull(createdClass1)
    assertEquals("Data Structures", createdClass1?.subjectName)
    assertEquals("Morning", createdClass1?.timing)
    assertEquals("user-123/exam-1/paper.jpg", createdClass1?.questionPaperPath)
    assertEquals(1, classViewModel.uiState.value.classes.size)

    // Add a second class to the same exam (without question paper)
    var createdClass2: ClassRoom? = null
    classViewModel.createClass(
      examId = "exam-1",
      session = "2022-2026",
      semester = "4th",
      timing = ClassTiming.EVENING,
      subjectName = "Algorithms",
      totalMarksText = "80",
      questionPaperBytes = null,
      fileExtension = null,
      onSuccess = { createdClass2 = it }
    )

    assertNotNull(createdClass2)
    assertEquals("Algorithms", createdClass2?.subjectName)
    assertEquals("Evening", createdClass2?.timing)
    assertNull(createdClass2?.questionPaperPath)
    assertEquals(2, classViewModel.uiState.value.classes.size)
  }

  @Test
  fun `verify ClassViewModel deleteClass flow`() = runTest {
    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Data Structures", totalMarks = 100)
    val class2 = ClassRoom(id = "class-2", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Evening", subjectName = "Operating Systems", totalMarks = 80)
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1, class2))
    val classViewModel = ClassViewModel(fakeClassRepo)

    classViewModel.loadClasses("exam-1")
    assertEquals(2, classViewModel.uiState.value.classes.size)

    var deleteSuccessCalled = false
    classViewModel.deleteClass("class-1") {
      deleteSuccessCalled = true
    }

    assertTrue(deleteSuccessCalled)
    assertEquals(1, classViewModel.uiState.value.classes.size)
    assertEquals("Operating Systems", classViewModel.uiState.value.classes.first().subjectName)
    assertEquals("Class deleted successfully.", classViewModel.uiState.value.feedbackMessage)

    // Failure case
    fakeClassRepo.shouldFail = true
    classViewModel.deleteClass("class-2")
    assertEquals(1, classViewModel.uiState.value.classes.size)
    assertEquals("Failed to delete class", classViewModel.uiState.value.deleteErrorMessage)
  }

  @Test
  fun `verify StudentViewModel validations and addStudent flow`() = runTest {
    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Software Engineering", totalMarks = 100)
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1))
    val fakeStudentRepo = FakeStudentRepository()
    val studentViewModel = StudentViewModel(fakeStudentRepo, fakeClassRepo)

    studentViewModel.loadClassAndStudents("class-1")
    assertEquals("Software Engineering", studentViewModel.uiState.value.classRoom?.subjectName)
    assertTrue(studentViewModel.uiState.value.students.isEmpty())

    // Validation checks
    studentViewModel.addStudent("class-1", "", "Robert Doe", "CS-01", onSuccess = {})
    assertEquals("Student name is required.", studentViewModel.uiState.value.addErrorMessage)

    studentViewModel.addStudent("class-1", "John Doe", "", "CS-01", onSuccess = {})
    assertEquals("Father name is required.", studentViewModel.uiState.value.addErrorMessage)

    studentViewModel.addStudent("class-1", "John Doe", "Robert Doe", "", onSuccess = {})
    assertEquals("Roll number is required.", studentViewModel.uiState.value.addErrorMessage)

    // Add Student 1 Success
    var addedStudent1: Student? = null
    studentViewModel.addStudent("class-1", "John Doe", "Robert Doe", "CS-01", onSuccess = { addedStudent1 = it })

    assertNotNull(addedStudent1)
    assertEquals("John Doe", addedStudent1?.name)
    assertEquals("Robert Doe", addedStudent1?.fatherName)
    assertEquals("CS-01", addedStudent1?.rollNumber)
    assertEquals(1, studentViewModel.uiState.value.students.size)
    assertEquals("Student added successfully.", studentViewModel.uiState.value.feedbackMessage)

    // Add Student 2 Success
    var addedStudent2: Student? = null
    studentViewModel.addStudent("class-1", "Alice Smith", "George Smith", "CS-02", onSuccess = { addedStudent2 = it })

    assertNotNull(addedStudent2)
    assertEquals(2, studentViewModel.uiState.value.students.size)
  }

  @Test
  fun `verify StudentViewModel deleteStudent flow`() = runTest {
    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Software Engineering", totalMarks = 100)
    val student1 = Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "John Doe", fatherName = "Robert Doe", rollNumber = "CS-01")
    val student2 = Student(id = "student-2", classId = "class-1", teacherId = "user-123", name = "Alice Smith", fatherName = "George Smith", rollNumber = "CS-02")
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1))
    val fakeStudentRepo = FakeStudentRepository(initialStudents = listOf(student1, student2))
    val studentViewModel = StudentViewModel(fakeStudentRepo, fakeClassRepo)

    studentViewModel.loadClassAndStudents("class-1")
    assertEquals(2, studentViewModel.uiState.value.students.size)

    var deleteCalled = false
    studentViewModel.deleteStudent("student-1") {
      deleteCalled = true
    }

    assertTrue(deleteCalled)
    assertEquals(1, studentViewModel.uiState.value.students.size)
    assertEquals("Alice Smith", studentViewModel.uiState.value.students.first().name)
    assertEquals("Student deleted successfully.", studentViewModel.uiState.value.feedbackMessage)

    // Failure case (e.g. permission/network failure)
    fakeStudentRepo.shouldFail = true
    studentViewModel.deleteStudent("student-2")
    // Ensure student is NOT removed from local list when Supabase deletion fails
    assertEquals(1, studentViewModel.uiState.value.students.size)
    assertEquals("Failed to delete student", studentViewModel.uiState.value.deleteErrorMessage)
  }

  @Test
  fun `verify StudentViewModel class-scoped roll-number and student isolation`() = runTest {
    val classA = ClassRoom(id = "class-A", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Subject A", totalMarks = 100)
    val classB = ClassRoom(id = "class-B", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Evening", subjectName = "Subject B", totalMarks = 100)
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(classA, classB))
    val fakeStudentRepo = FakeStudentRepository()
    val studentViewModel = StudentViewModel(fakeStudentRepo, fakeClassRepo)

    // Add student with Roll CS-01 in Class A
    studentViewModel.addStudent("class-A", "John in A", "Father A", "CS-01", onSuccess = {})
    assertEquals(1, studentViewModel.uiState.value.students.size)

    // Attempting same Roll CS-01 in Class A fails with duplicate error
    studentViewModel.addStudent("class-A", "Duplicate John", "Father X", "CS-01", onSuccess = {})
    assertEquals("A student with roll number 'CS-01' already exists in this class.", studentViewModel.uiState.value.addErrorMessage)

    // Same Roll CS-01 in Class B succeeds (class-scoped uniqueness)
    val studentViewModelB = StudentViewModel(fakeStudentRepo, fakeClassRepo)
    var studentInBCreated = false
    studentViewModelB.addStudent("class-B", "Student in B", "Father B", "CS-01", onSuccess = { studentInBCreated = true })
    assertTrue(studentInBCreated)
    assertEquals(1, studentViewModelB.uiState.value.students.size)

    // Load Class A students again: verify isolation (only John in A, not Student in B)
    studentViewModel.loadClassAndStudents("class-A")
    assertEquals(1, studentViewModel.uiState.value.students.size)
    assertEquals("John in A", studentViewModel.uiState.value.students.first().name)
  }

  @Test
  fun `verify ScanPaperViewModel multi-page management and renumbering`() = runTest {
    val fakePaperRepo = FakePaperRepository()
    val viewModel = ScanPaperViewModel(fakePaperRepo)

    assertEquals(0, viewModel.uiState.value.pages.size)

    // Add 3 pages
    val uri1 = Uri.parse("content://media/external/images/media/1")
    val uri2 = Uri.parse("content://media/external/images/media/2")
    val uri3 = Uri.parse("content://media/external/images/media/3")

    viewModel.addPage(uri1)
    viewModel.addPages(listOf(uri2, uri3))

    assertEquals(3, viewModel.uiState.value.pages.size)
    assertEquals(1, viewModel.uiState.value.pages[0].pageNumber)
    assertEquals(2, viewModel.uiState.value.pages[1].pageNumber)
    assertEquals(3, viewModel.uiState.value.pages[2].pageNumber)

    // Remove page 2 -> check re-indexing
    val page2Id = viewModel.uiState.value.pages[1].id
    viewModel.removePage(page2Id)

    assertEquals(2, viewModel.uiState.value.pages.size)
    assertEquals(1, viewModel.uiState.value.pages[0].pageNumber)
    assertEquals(2, viewModel.uiState.value.pages[1].pageNumber)
    assertEquals(uri3, viewModel.uiState.value.pages[1].uri)
  }

  @Test
  fun `verify ScanPaperViewModel upload failure preserves pages in memory`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakePaperRepo = FakePaperRepository(shouldFail = true)
    val viewModel = ScanPaperViewModel(fakePaperRepo)

    val uri1 = Uri.parse("content://media/external/images/media/1")
    viewModel.addPage(uri1)

    var successCalled = false
    viewModel.finishScan(context, "student-1", "class-1", onSuccess = { successCalled = true })

    assertFalse(successCalled)
    assertFalse(viewModel.uiState.value.isUploading)
    assertEquals("Failed to upload paper", viewModel.uiState.value.uploadError)
    // Pages must remain intact so teacher doesn't lose them
    assertEquals(1, viewModel.uiState.value.pages.size)
  }

  @Test
  fun `verify StudentDetailViewModel loadPapers and deletePaper with storage cleanup`() = runTest {
    val student = Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Zaid Ali", fatherName = "Ali", rollNumber = "CS-05")
    val classRoom = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Database", totalMarks = 100)
    val paper1 = StudentPaper(
      id = "paper-1",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 2,
      pages = listOf(
        StudentPaperPage(id = "page-1", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 1, storagePath = "user-123/class-1/student-1/paper-1/page_1.jpg"),
        StudentPaperPage(id = "page-2", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 2, storagePath = "user-123/class-1/student-1/paper-1/page_2.jpg"),
      )
    )

    val fakeStudentRepo = FakeStudentRepository(initialStudents = listOf(student))
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(classRoom))
    val fakePaperRepo = FakePaperRepository(initialPapers = listOf(paper1))

    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)

    viewModel.loadStudentAndPapers("student-1")

    assertEquals("Zaid Ali", viewModel.uiState.value.student?.name)
    assertEquals("Database", viewModel.uiState.value.classRoom?.subjectName)
    assertEquals(1, viewModel.uiState.value.papers.size)
    assertEquals(2, viewModel.uiState.value.papers.first().totalPages)

    // Delete paper
    var deleteSuccess = false
    viewModel.deletePaper(paper1) { deleteSuccess = true }

    assertTrue(deleteSuccess)
    assertTrue(viewModel.uiState.value.papers.isEmpty())
    assertEquals("Paper submission deleted.", viewModel.uiState.value.feedbackMessage)
    // Check that storage files were deleted as well
    assertEquals(2, fakePaperRepo.deletedStoragePaths.size)
    assertTrue(fakePaperRepo.deletedStoragePaths.contains("user-123/class-1/student-1/paper-1/page_1.jpg"))
  }

  @Test
  fun `verify StudentDetailViewModel runOcrForPage success and failure flows`() = runTest {
    val student = Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Zaid Ali", fatherName = "Ali", rollNumber = "CS-05")
    val classRoom = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "Database", totalMarks = 100)
    val paper = StudentPaper(
      id = "paper-1",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 1,
      pages = listOf(
        StudentPaperPage(id = "page-1", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 1, storagePath = "path/page_1.jpg"),
      )
    )

    val fakeStudentRepo = FakeStudentRepository(listOf(student))
    val fakeClassRepo = FakeClassRepository(listOf(classRoom))
    val fakePaperRepo = FakePaperRepository(listOf(paper))
    val fakeOcrRepo = FakeOcrRepository(shouldSucceed = true, cannedOcrText = "Question 1: Database normalization reduces redundancy.")

    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo, fakeOcrRepo)

    // Run OCR successfully
    viewModel.runOcrForPage("paper-1", "page-1")
    assertEquals("Question 1: Database normalization reduces redundancy.", viewModel.uiState.value.ocrTextByPageId["page-1"])
    assertTrue(viewModel.uiState.value.ocrLoadingPageIds.isEmpty())
    assertNull(viewModel.uiState.value.ocrErrorByPageId["page-1"])
    assertEquals("Handwriting transcribed successfully.", viewModel.uiState.value.feedbackMessage)
    assertEquals("paper-1", fakeOcrRepo.lastPaperId)
    assertEquals("page-1", fakeOcrRepo.lastPageId)

    // Run OCR failure
    fakeOcrRepo.shouldSucceed = false
    viewModel.runOcrForPage("paper-1", "page-2")
    assertEquals("OCR provider timed out.", viewModel.uiState.value.ocrErrorByPageId["page-2"])
    assertTrue(viewModel.uiState.value.ocrLoadingPageIds.isEmpty())

    // Clear error
    viewModel.clearOcrError("page-2")
    assertNull(viewModel.uiState.value.ocrErrorByPageId["page-2"])
  }

  @Test
  fun studentDetailViewModel_aiEvaluationResponseParsingAndDismiss() = runTest {
    val json = """
      {
        "status": "needs_review",
        "percentageAvailable": false,
        "evaluation": {
          "id": "eval-123",
          "paperId": "paper-1",
          "studentId": "student-1",
          "classId": "class-1",
          "status": "needs_review",
          "totalMarksObtained": 5,
          "totalMarks": 100,
          "extractedQuestionTotalMarks": 20,
          "percentage": null,
          "percentageAvailable": false,
          "reviewRequired": true,
          "reviewReason": "Extracted question marks (20) do not reliably match the configured class total marks (100).",
          "questions": [
            {
              "question_number": "1",
              "question_text": "what is motherboard ?",
              "maximum_marks": 10,
              "student_answer": "A electric circuit ,",
              "expected_answer": "[Provisional Reference Answer]: Main printed circuit board.",
              "marking_criteria": "[Provisional Criteria]: Accurate definition earns full marks.",
              "awarded_marks": 3,
              "feedback": "Partially correct.",
              "review_required": false,
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated"
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = com.example.data.model.evaluationJsonParser.decodeFromString<com.example.data.model.AiEvaluationResponse>(json)
    assertNotNull(parsed.evaluation)
    assertEquals("needs_review", parsed.status)
    assertFalse(parsed.percentageAvailable)
    assertEquals(5.0, parsed.evaluation?.totalMarksObtained)
    assertEquals(100.0, parsed.evaluation?.totalMarks)
    assertEquals(20.0, parsed.evaluation?.extractedQuestionTotalMarks)
    assertEquals("5", parsed.evaluation?.displayTotalMarksObtained)
    assertEquals("100", parsed.evaluation?.displayTotalMarks)
    assertTrue(parsed.evaluation?.reviewRequired == true)
    assertEquals("Extracted question marks (20) do not reliably match the configured class total marks (100).", parsed.evaluation?.reviewReason)

    val q = parsed.evaluation?.questions?.firstOrNull()
    assertNotNull(q)
    assertEquals("1", q?.questionNumber)
    assertEquals("what is motherboard ?", q?.questionText)
    assertEquals(10.0, q?.maximumMarks)
    assertEquals(3.0, q?.awardedMarks)
    assertEquals("3", q?.displayAwardedMarks)
    assertEquals("10", q?.displayMaxMarks)
    assertEquals("A electric circuit ,", q?.studentAnswer)
    assertEquals("ai_generated", q?.expectedAnswerSource)
    assertEquals("ai_generated", q?.markingCriteriaSource)
    assertFalse(q?.reviewRequired == true)

    val fakeStudentRepo = FakeStudentRepository()
    val fakeClassRepo = FakeClassRepository()
    val fakePaperRepo = FakePaperRepository()
    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)

    viewModel.dismissEvaluationDialog()
    assertNull(viewModel.uiState.value.evaluationResult)
    assertNull(viewModel.uiState.value.evaluationResultJson)
    assertNull(viewModel.uiState.value.evaluationError)
    assertFalse(viewModel.uiState.value.isApproving)
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertNull(viewModel.uiState.value.approvalError)
  }

  @Test
  fun studentDetailViewModel_alreadyApprovedStatePreventsDuplicateRpc() = runTest {
    val fakeStudentRepo = FakeStudentRepository()
    val fakeClassRepo = FakeClassRepository()
    val fakePaperRepo = FakePaperRepository()
    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)

    // Blank IDs should be rejected immediately
    viewModel.approveEvaluation("", "")
    assertFalse(viewModel.uiState.value.isApproving)

    // Clear error
    viewModel.clearApprovalError()
    assertNull(viewModel.uiState.value.approvalError)
  }

  @Test
  fun evaluationModels_parseAiEvaluationResponse_handlesFlatAndSnakeCaseJson() = runTest {
    // Tests flat JSON with snake_case and numeric question_number
    val flatSnakeJson = """
      {
        "status": "needs_review",
        "total_marks_obtained": "7.5",
        "total_marks": 50,
        "review_required": true,
        "review_reason": "Teacher review recommended.",
        "questions": [
          {
            "question_number": 2,
            "question_text": "Define RAM",
            "maximum_marks": "10",
            "awarded_marks": 7.5,
            "student_answer": "Random Access Memory",
            "expected_answer": "Random Access Memory",
            "marking_criteria": "Accurate definition",
            "feedback": "Correct definition provided.",
            "review_required": false,
            "expected_answer_source": "ai_generated",
            "marking_criteria_source": "ai_generated"
          }
        ]
      }
    """.trimIndent()

    val parsed = com.example.data.model.parseAiEvaluationResponse(flatSnakeJson, fallbackPaperId = "paper-99")
    assertNotNull(parsed)
    assertNotNull(parsed?.evaluation)
    assertEquals("needs_review", parsed?.status)
    assertEquals("paper-99", parsed?.evaluation?.paperId)
    assertEquals(7.5, parsed?.evaluation?.totalMarksObtained)
    assertEquals(50.0, parsed?.evaluation?.totalMarks)
    assertTrue(parsed?.evaluation?.reviewRequired == true)
    assertEquals("Teacher review recommended.", parsed?.evaluation?.reviewReason)

    val q = parsed?.evaluation?.questions?.firstOrNull()
    assertNotNull(q)
    assertEquals("2", q?.questionNumber)
    assertEquals("Define RAM", q?.questionText)
    assertEquals(10.0, q?.maximumMarks)
    assertEquals(7.5, q?.awardedMarks)
    assertEquals("Random Access Memory", q?.studentAnswer)
    assertEquals("ai_generated", q?.expectedAnswerSource)
    assertEquals("ai_generated", q?.markingCriteriaSource)
  }

  @Test
  fun studentDetailViewModel_approvedEvaluationIsReadOnlyAndPreservesNavigationState() = runTest {
    val fakeStudentRepo = FakeStudentRepository()
    val fakeClassRepo = FakeClassRepository()
    val fakePaperRepo = FakePaperRepository()
    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)

    val approvedJson = """
      {
        "status": "approved",
        "percentageAvailable": true,
        "evaluation": {
          "id": "eval-approved-1",
          "paperId": "paper-approved-1",
          "studentId": "student-1",
          "classId": "class-1",
          "status": "approved",
          "totalMarksObtained": 18.0,
          "totalMarks": 20.0,
          "extractedQuestionTotalMarks": 20.0,
          "percentage": 90.0,
          "percentageAvailable": true,
          "reviewRequired": false,
          "questions": [
            {
              "id": "q-1",
              "question_number": "1",
              "question_text": "Explain polymorphism.",
              "maximum_marks": 10.0,
              "student_answer": "Ability of objects to take multiple forms.",
              "expected_answer": "Objects taking multiple forms.",
              "marking_criteria": "Full marks for clear definition.",
              "awarded_marks": 9.0,
              "feedback": "Well explained.",
              "review_required": false
            }
          ]
        }
      }
    """.trimIndent()

    val parsedApproved = com.example.data.model.parseAiEvaluationResponse(
      approvedJson,
      fallbackPaperId = "paper-approved-1",
    )
    assertNotNull(parsedApproved)

    viewModel.setEvaluationResultForTesting("paper-approved-1", parsedApproved)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.status)
    assertNull(viewModel.uiState.value.editedQuestions)

    // Attempting to update marks or feedback on an approved evaluation must be ignored
    viewModel.onUpdateQuestion(
      questionId = "q-1",
      questionNumber = "1",
      awardedMarks = 2.0,
      feedback = "Tampered feedback",
    )
    assertNull(viewModel.uiState.value.editedQuestions)
    assertEquals(
      9.0,
      viewModel.uiState.value.evaluationResult?.evaluation?.questions?.firstOrNull()?.awardedMarks,
    )

    // Re-opening approved evaluation preserves loaded approved state without triggering AI evaluation
    viewModel.openApprovedEvaluation("paper-approved-1")
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)

    // Calling runAiEvaluation on an already-approved paper sets one-shot navigation and does not re-grade
    viewModel.runAiEvaluation("paper-approved-1")
    assertEquals("paper-approved-1", viewModel.uiState.value.pendingReviewNavigationPaperId)
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)

    // Consuming navigation clears the pending paper ID so back navigation does not re-open TeacherReview
    viewModel.consumeReviewNavigation()
    assertNull(viewModel.uiState.value.pendingReviewNavigationPaperId)
  }

  @Test
  fun `verify StorageCleanupHelper normalizes paths and StudentDetailViewModel deletes individual page`() = runTest {
    val normalized = com.example.data.repository.StorageCleanupHelper.normalizeStoragePaths(
      rawPaths = listOf(
        "user-123/class-1/student-1/paper-1/page_1.jpg",
        "/student-papers/user-123/class-1/student-1/paper-1/page_1.jpg",
        "  /user-123/class-1/student-1/paper-1/page_2.jpg  ",
        "",
        null,
      ),
      bucketName = "student-papers",
    )
    assertEquals(
      listOf(
        "user-123/class-1/student-1/paper-1/page_1.jpg",
        "user-123/class-1/student-1/paper-1/page_2.jpg",
      ),
      normalized,
    )

    val page1 = StudentPaperPage(
      id = "page-1",
      paperId = "paper-1",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 1,
      storagePath = "user-123/class-1/student-1/paper-1/page_1.jpg",
    )
    val page2 = StudentPaperPage(
      id = "page-2",
      paperId = "paper-1",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 2,
      storagePath = "user-123/class-1/student-1/paper-1/page_2.jpg",
    )
    val paper = StudentPaper(
      id = "paper-1",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 2,
      pages = listOf(page1, page2),
    )

    val fakeStudentRepo = FakeStudentRepository(
      listOf(Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Zaid", fatherName = "Ali", rollNumber = "01"))
    )
    val fakeClassRepo = FakeClassRepository(
      listOf(ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "DB", totalMarks = 100))
    )
    val fakePaperRepo = FakePaperRepository(listOf(paper))
    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)
    viewModel.loadStudentAndPapers("student-1")

    // Deleting page 1 keeps page 2 intact, resequences page 2 to page_number = 1, and removes only page 1's storage file
    viewModel.deletePaperPage(page1)
    assertEquals(listOf("user-123/class-1/student-1/paper-1/page_1.jpg"), fakePaperRepo.deletedStoragePaths)
    assertEquals(1, viewModel.uiState.value.papers.size)
    assertEquals(1, viewModel.uiState.value.papers.first().totalPages)
    assertEquals(1, viewModel.uiState.value.papers.first().pages.size)
    assertEquals("page-2", viewModel.uiState.value.papers.first().pages.first().id)
    assertEquals(1, viewModel.uiState.value.papers.first().pages.first().pageNumber)

    // Deleting the last remaining page removes its storage file and removes the paper
    viewModel.deletePaperPage(page2)
    assertEquals(
      listOf(
        "user-123/class-1/student-1/paper-1/page_1.jpg",
        "user-123/class-1/student-1/paper-1/page_2.jpg",
      ),
      fakePaperRepo.deletedStoragePaths,
    )
    assertTrue(viewModel.uiState.value.papers.isEmpty())

    // Verify generic non-RestException 404/not-found messages are NOT treated as already-missing storage objects
    assertFalse(
      com.example.data.repository.StorageCleanupHelper.isAlreadyMissingStorageError(
        RuntimeException("404 Bucket not found")
      )
    )
    assertFalse(
      com.example.data.repository.StorageCleanupHelper.isAlreadyMissingStorageError(
        RuntimeException("404 Object not found")
      )
    )

    // Verify deleteFromBucket surfaces Storage errors instead of silently succeeding
    var simulateStorageError = true
    val deletedChunks = mutableListOf<List<String>>()
    val fakeBucketApi = java.lang.reflect.Proxy.newProxyInstance(
      io.github.jan.supabase.storage.BucketApi::class.java.classLoader,
      arrayOf(io.github.jan.supabase.storage.BucketApi::class.java),
    ) { _, method, args ->
      when (method.name) {
        "delete" -> {
          if (simulateStorageError) {
            throw RuntimeException("Storage permission denied")
          }
          @Suppress("UNCHECKED_CAST")
          val pathsArg = args?.firstOrNull() as? Collection<String> ?: emptyList()
          deletedChunks.add(pathsArg.toList())
          Unit
        }
        "exists" -> false
        else -> throw UnsupportedOperationException("Unexpected BucketApi call: ${method.name}")
      }
    } as io.github.jan.supabase.storage.BucketApi

    val storageFailureResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = listOf("user-123/class-1/student-1/paper-1/page_1.jpg"),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(storageFailureResult.isFailure)
    assertTrue(
      storageFailureResult.exceptionOrNull()?.message?.contains("Failed to delete image files from cloud storage") == true
    )

    // When Storage delete succeeds, deleteFromBucket completes normally
    simulateStorageError = false
    val verifiedDeleteResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = listOf("user-123/class-1/student-1/paper-1/page_1.jpg"),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(verifiedDeleteResult.isSuccess)
    assertEquals(1, deletedChunks.size)
  }

  @Test
  fun `verify deletePaperPage and deletePaper update page numbering and clear evaluation state`() = runTest {
    val page1 = StudentPaperPage(
      id = "page-1",
      paperId = "paper-prov",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 1,
      storagePath = "user-123/class-1/student-1/paper-prov/page_1.jpg",
    )
    val page2 = StudentPaperPage(
      id = "page-2",
      paperId = "paper-prov",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 2,
      storagePath = "user-123/class-1/student-1/paper-prov/page_2.jpg",
    )
    val provisionalPaper = StudentPaper(
      id = "paper-prov",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 2,
      status = "completed",
      ocrStatus = "completed",
      combinedOcrText = "Page 1 and Page 2 text",
      evaluation = EvaluationStatus(status = "completed"),
      pages = listOf(page1, page2),
    )

    val approvedPage = StudentPaperPage(
      id = "page-app-1",
      paperId = "paper-app",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 1,
      storagePath = "user-123/class-1/student-1/paper-app/page_1.jpg",
    )
    val approvedPaper = StudentPaper(
      id = "paper-app",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 1,
      status = "approved",
      evaluation = EvaluationStatus(status = "approved"),
      pages = listOf(approvedPage),
    )

    val fakeStudentRepo = FakeStudentRepository(
      listOf(Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Zaid", fatherName = "Ali", rollNumber = "01"))
    )
    val fakeClassRepo = FakeClassRepository(
      listOf(ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "DB", totalMarks = 100))
    )
    val fakePaperRepo = FakePaperRepository(listOf(provisionalPaper, approvedPaper))
    val viewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)
    viewModel.loadStudentAndPapers("student-1")

    val provisionalEval = AiEvaluationResponse(
      status = "completed",
      percentageAvailable = true,
      evaluation = EvaluationDetail(
        id = "eval-prov",
        paperId = "paper-prov",
        studentId = "student-1",
        classId = "class-1",
        teacherId = "user-123",
        status = "completed",
        totalMarksObtained = 80.0,
        totalMarks = 100.0,
        percentage = 80.0,
        percentageAvailable = true,
      )
    )
    viewModel.setEvaluationResultForTesting("paper-prov", provisionalEval)
    assertEquals("paper-prov", viewModel.uiState.value.evaluatedPaperId)
    assertNotNull(viewModel.uiState.value.evaluationResult)

    // 1. Deleting page 1 from the 2-page paper invalidates its outdated evaluation and resets paper status & page numbering
    viewModel.deletePaperPage(page1)
    assertNull(viewModel.uiState.value.evaluatedPaperId)
    assertNull(viewModel.uiState.value.evaluationResult)
    val modifiedProvPaper = viewModel.uiState.value.papers.first { it.id == "paper-prov" }
    assertEquals(1, modifiedProvPaper.totalPages)
    assertEquals("scanned", modifiedProvPaper.status)
    assertNull(modifiedProvPaper.evaluation)
    assertEquals(1, modifiedProvPaper.pages.first().pageNumber)

    // 2. If the remaining 1-page paper is evaluated again and its last page is deleted, evaluationResult & evaluatedPaperId are cleared
    viewModel.setEvaluationResultForTesting("paper-prov", provisionalEval)
    assertEquals("paper-prov", viewModel.uiState.value.evaluatedPaperId)
    assertNotNull(viewModel.uiState.value.evaluationResult)

    viewModel.deletePaperPage(page2)
    assertNull(viewModel.uiState.value.evaluatedPaperId)
    assertNull(viewModel.uiState.value.evaluationResult)
    assertFalse(viewModel.uiState.value.papers.any { it.id == "paper-prov" })

    // 3. Deleting an approved paper succeeds, cleans up storage paths, and clears evaluation state
    val approvedEval = AiEvaluationResponse(
      status = "approved",
      percentageAvailable = true,
      evaluation = EvaluationDetail(
        id = "eval-app",
        paperId = "paper-app",
        studentId = "student-1",
        classId = "class-1",
        teacherId = "user-123",
        status = "approved",
        totalMarksObtained = 95.0,
        totalMarks = 100.0,
        percentage = 95.0,
        percentageAvailable = true,
      )
    )
    viewModel.setEvaluationResultForTesting("paper-app", approvedEval)
    viewModel.deletePaper(approvedPaper)
    assertNull(viewModel.uiState.value.deletePaperError)
    assertTrue(fakePaperRepo.deletedStoragePaths.contains("user-123/class-1/student-1/paper-app/page_1.jpg"))
    assertFalse(viewModel.uiState.value.papers.any { it.id == "paper-app" })
    assertNull(viewModel.uiState.value.evaluatedPaperId)
    assertNull(viewModel.uiState.value.evaluationResult)
  }

  @Test
  fun `verify all five deletion operations succeed and failures preserve ViewModel state`() = runTest {
    val page1 = StudentPaperPage(
      id = "page-1",
      paperId = "paper-1",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 1,
      storagePath = "user-123/class-1/student-1/paper-1/page_1.jpg",
    )
    val page2 = StudentPaperPage(
      id = "page-2",
      paperId = "paper-1",
      studentId = "student-1",
      teacherId = "user-123",
      pageNumber = 2,
      storagePath = "user-123/class-1/student-1/paper-1/page_2.jpg",
    )
    val paper1 = StudentPaper(
      id = "paper-1",
      studentId = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      totalPages = 2,
      status = "completed",
      evaluation = EvaluationStatus(status = "completed"),
      pages = listOf(page1, page2),
    )

    val student1 = Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Zaid", fatherName = "Ali", rollNumber = "01")
    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2022-2026", semester = "4th", timing = "Morning", subjectName = "DB", totalMarks = 100)
    val exam1 = Exam(id = "exam-1", teacherId = "user-123", name = "Mid Term 2026", type = "Mid Term", date = "2026-10-05")

    val fakeStudentRepo = FakeStudentRepository(initialStudents = listOf(student1), shouldFail = true)
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1), shouldFail = true)
    val fakeExamRepo = FakeExamRepository(initialExams = listOf(exam1), shouldFail = true)
    val fakePaperRepo = FakePaperRepository(initialPapers = listOf(paper1), shouldFail = false)

    val detailViewModel = StudentDetailViewModel(
      FakeStudentRepository(initialStudents = listOf(student1)),
      FakeClassRepository(initialClasses = listOf(class1)),
      fakePaperRepo,
    )
    detailViewModel.loadStudentAndPapers("student-1")

    val provisionalEval = AiEvaluationResponse(
      status = "completed",
      percentageAvailable = true,
      evaluation = EvaluationDetail(
        id = "eval-1",
        paperId = "paper-1",
        studentId = "student-1",
        classId = "class-1",
        teacherId = "user-123",
        status = "completed",
        totalMarksObtained = 75.0,
        totalMarks = 100.0,
        percentage = 75.0,
        percentageAvailable = true,
      )
    )
    detailViewModel.setEvaluationResultForTesting("paper-1", provisionalEval)

    // 1. When repository fails, ViewModel preserves existing papers, pages, and evaluation state and surfaces the error
    fakePaperRepo.shouldFail = true
    detailViewModel.deletePaperPage(page1)
    assertEquals("Failed to delete page", detailViewModel.uiState.value.deletePaperError)
    assertTrue(fakePaperRepo.deletedStoragePaths.isEmpty())
    assertEquals(2, detailViewModel.uiState.value.papers.first().pages.size)
    assertEquals("paper-1", detailViewModel.uiState.value.evaluatedPaperId)
    assertNotNull(detailViewModel.uiState.value.evaluationResult)

    detailViewModel.deletePaper(paper1)
    assertEquals("Failed to delete paper", detailViewModel.uiState.value.deletePaperError)
    assertTrue(fakePaperRepo.deletedStoragePaths.isEmpty())
    assertEquals(1, detailViewModel.uiState.value.papers.size)

    val studentViewModel = StudentViewModel(
      fakeStudentRepo,
      FakeClassRepository(initialClasses = listOf(class1)),
    )
    fakeStudentRepo.shouldFail = false
    studentViewModel.loadClassAndStudents("class-1")
    fakeStudentRepo.shouldFail = true
    studentViewModel.deleteStudent("student-1")
    assertEquals("Failed to delete student", studentViewModel.uiState.value.deleteErrorMessage)
    assertEquals(1, studentViewModel.uiState.value.students.size)

    val classViewModel = ClassViewModel(fakeClassRepo)
    fakeClassRepo.shouldFail = false
    classViewModel.loadClasses("exam-1")
    fakeClassRepo.shouldFail = true
    classViewModel.deleteClass("class-1")
    assertEquals("Failed to delete class", classViewModel.uiState.value.deleteErrorMessage)
    assertEquals(1, classViewModel.uiState.value.classes.size)

    val examViewModel = ExamViewModel(fakeExamRepo)
    fakeExamRepo.shouldFail = false
    examViewModel.loadExams()
    fakeExamRepo.shouldFail = true
    examViewModel.deleteExam("exam-1")
    assertEquals("Failed to delete exam", examViewModel.uiState.value.deleteErrorMessage)
    assertEquals(1, examViewModel.uiState.value.exams.size)

    // 2. When repository succeeds, all five deletion operations remove the item from in-memory state
    fakePaperRepo.shouldFail = false
    detailViewModel.deletePaperPage(page1)
    assertNull(detailViewModel.uiState.value.deletePaperError)
    assertEquals(1, detailViewModel.uiState.value.papers.first().pages.size)
    assertEquals(1, detailViewModel.uiState.value.papers.first().pages.first().pageNumber)

    detailViewModel.deletePaper(detailViewModel.uiState.value.papers.first())
    assertNull(detailViewModel.uiState.value.deletePaperError)
    assertTrue(detailViewModel.uiState.value.papers.isEmpty())

    fakeStudentRepo.shouldFail = false
    studentViewModel.deleteStudent("student-1")
    assertNull(studentViewModel.uiState.value.deleteErrorMessage)
    assertTrue(studentViewModel.uiState.value.students.isEmpty())

    fakeClassRepo.shouldFail = false
    classViewModel.deleteClass("class-1")
    assertNull(classViewModel.uiState.value.deleteErrorMessage)
    assertTrue(classViewModel.uiState.value.classes.isEmpty())

    fakeExamRepo.shouldFail = false
    examViewModel.deleteExam("exam-1")
    assertNull(examViewModel.uiState.value.deleteErrorMessage)
    assertTrue(examViewModel.uiState.value.exams.isEmpty())
  }

  @Test
  fun testCrossPageVerbatimStudentAnswerAndEqualMarks4x20Parsing() {
    val page1Verbatim = "Deadlock occurs when a set of processes are blocked because each process holds a resource"
    val page2ContinuationVerbatim = "and waits for another resource acquired by some other process in the same set."
    val expectedCombinedVerbatim = "$page1Verbatim\\n\\n$page2ContinuationVerbatim"

    val json = """
      {
        "status": "completed",
        "percentageAvailable": true,
        "evaluation": {
          "id": "eval-equal-80",
          "paperId": "paper-equal-80",
          "studentId": "student-1",
          "classId": "class-equal-80",
          "teacherId": "teacher-1",
          "status": "completed",
          "totalMarksObtained": 72,
          "totalMarks": 80,
          "extractedQuestionTotalMarks": 80,
          "percentage": 90.0,
          "percentageAvailable": true,
          "reviewRequired": false,
          "reviewReason": null,
          "questions": [
            {
              "question_number": "1",
              "question_text": "Define deadlock and its four necessary conditions.",
              "maximum_marks": 20,
              "student_answer": "$expectedCombinedVerbatim",
              "expected_answer": "[Provisional Reference Answer]: Deadlock is a state where processes are blocked waiting on each other...",
              "marking_criteria": "[Provisional Criteria]: - Definition: 8 marks\\n- Four conditions: 12 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 18,
              "feedback": "Clear definition spanning pages 1 and 2.",
              "review_required": false,
              "review_reason": null
            },
            {
              "question_number": "2",
              "question_text": "Explain paging vs segmentation.",
              "maximum_marks": 20,
              "student_answer": "Paging divides memory into fixed-size frames whereas segmentation uses logical variable-sized segments.",
              "expected_answer": "[Provisional Reference Answer]: Paging vs segmentation...",
              "marking_criteria": "[Provisional Criteria]: - Paging: 10 marks\\n- Segmentation: 10 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 18,
              "feedback": "Good comparison.",
              "review_required": false,
              "review_reason": null
            },
            {
              "question_number": "3",
              "question_text": "Explain virtual memory and page faults.",
              "maximum_marks": 20,
              "student_answer": "Virtual memory allows execution of processes not completely in main memory.",
              "expected_answer": "[Provisional Reference Answer]: Virtual memory...",
              "marking_criteria": "[Provisional Criteria]: - Virtual memory: 10 marks\\n- Page faults: 10 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 16,
              "feedback": "Covered virtual memory concept.",
              "review_required": false,
              "review_reason": null
            },
            {
              "question_number": "4",
              "question_text": "Describe disk scheduling algorithms.",
              "maximum_marks": 20,
              "student_answer": "FCFS, SSTF, SCAN, and C-SCAN schedule disk head movement.",
              "expected_answer": "[Provisional Reference Answer]: Disk scheduling...",
              "marking_criteria": "[Provisional Criteria]: - Each algorithm: 5 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 20,
              "feedback": "All four algorithms explained.",
              "review_required": false,
              "review_reason": null
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = parseAiEvaluationResponse(json)
    assertNotNull(parsed)
    val eval = parsed!!.evaluation!!
    assertEquals(80.0, eval.totalMarks!!, 0.001)
    assertEquals(80.0, eval.extractedQuestionTotalMarks!!, 0.001)
    assertEquals(72.0, eval.totalMarksObtained!!, 0.001)
    assertEquals(90.0, eval.percentage!!, 0.001)
    assertTrue(eval.percentageAvailable)
    assertFalse(eval.reviewRequired)
    assertEquals(4, eval.questions.size)
    assertTrue(eval.questions.all { it.maximumMarks == 20.0 })

    val q1 = eval.questions.first()
    assertFalse(q1.studentAnswer!!.startsWith("Q1"))
    assertFalse(q1.studentAnswer!!.startsWith("[Page"))
    assertTrue(q1.studentAnswer!!.contains(page1Verbatim))
    assertTrue(q1.studentAnswer!!.contains(page2ContinuationVerbatim))
  }

  @Test
  fun testRandomOrderQuestionsSeparationAndNoQuestionLabelsInStudentAnswer() {
    val json = """
      {
        "status": "needs_review",
        "percentageAvailable": false,
        "evaluation": {
          "id": "eval-random-order",
          "paperId": "paper-random-order",
          "studentId": "student-1",
          "classId": "class-1",
          "teacherId": "teacher-1",
          "status": "needs_review",
          "totalMarksObtained": 55,
          "totalMarks": 80,
          "extractedQuestionTotalMarks": 80,
          "percentage": null,
          "percentageAvailable": false,
          "reviewRequired": true,
          "reviewReason": "Evaluation requires teacher review before final percentage calculation.",
          "questions": [
            {
              "question_number": "1",
              "question_text": "Explain photosynthesis.",
              "maximum_marks": 20,
              "student_answer": "Photosynthesis converts light energy into chemical energy in chloroplasts.",
              "expected_answer": "[Provisional Reference Answer]: Photosynthesis...",
              "marking_criteria": "[Provisional Criteria]: 20 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 18,
              "feedback": "Well explained.",
              "review_required": false,
              "review_reason": null
            },
            {
              "question_number": "2",
              "question_text": "Explain cellular respiration.",
              "maximum_marks": 20,
              "student_answer": "[No answer detected]",
              "expected_answer": "[Provisional Reference Answer]: Cellular respiration...",
              "marking_criteria": "[Provisional Criteria]: 20 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 0,
              "feedback": "0 marks awarded: No student answer was found for this compulsory question.",
              "review_required": true,
              "review_reason": "Student did not attempt this compulsory question."
            },
            {
              "question_number": "3",
              "question_text": "Describe mitosis.",
              "maximum_marks": 20,
              "student_answer": "Mitosis is somatic cell division producing two genetically identical diploid daughter cells.",
              "expected_answer": "[Provisional Reference Answer]: Mitosis...",
              "marking_criteria": "[Provisional Criteria]: 20 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 19,
              "feedback": "Accurate description.",
              "review_required": false,
              "review_reason": null
            },
            {
              "question_number": "4",
              "question_text": "Define osmosis and boiling point of water.",
              "maximum_marks": 20,
              "student_answer": "100°C is the boiling point of water at standard pressure, and osmosis is water diffusion across a semipermeable membrane.",
              "expected_answer": "[Provisional Reference Answer]: Osmosis...",
              "marking_criteria": "[Provisional Criteria]: 20 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 18,
              "feedback": "Correct definition and numeric value.",
              "review_required": false,
              "review_reason": null
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = parseAiEvaluationResponse(json)
    assertNotNull(parsed)
    val questions = parsed!!.evaluation!!.questions
    assertEquals(4, questions.size)

    val q1 = questions.first { it.questionNumber == "1" }
    val q2 = questions.first { it.questionNumber == "2" }
    val q3 = questions.first { it.questionNumber == "3" }
    val q4 = questions.first { it.questionNumber == "4" }

    // Q1 and Q4 remain strictly separate
    assertEquals("Photosynthesis converts light energy into chemical energy in chloroplasts.", q1.studentAnswer)
    assertFalse(q1.studentAnswer!!.contains("osmosis"))
    assertEquals(
      "100°C is the boiling point of water at standard pressure, and osmosis is water diffusion across a semipermeable membrane.",
      q4.studentAnswer
    )
    // Q2 remains unanswered
    assertEquals("[No answer detected]", q2.studentAnswer)
    assertEquals(0.0, q2.awardedMarks!!, 0.001)
    // No artificial question labels in any answered question
    for (q in listOf(q1, q3, q4)) {
      assertFalse(q.studentAnswer!!.startsWith("Q${q.questionNumber}"))
      assertFalse(q.studentAnswer!!.startsWith("${q.questionNumber}."))
      assertFalse(q.studentAnswer!!.startsWith("${q.questionNumber})"))
    }
  }

  @Test
  fun testOptionalQuestionsAttempt7OutOf10AndInternalOrChoicesEffectiveMarks() {
    // 1. "Attempt any 7 out of 10" where each question is 10 marks -> effective exam total is 70, not 100
    val dbQuestions7Of10 = (1..10).map { i ->
      val attempted = i <= 7
      QuestionDbRecord(
        id = "q-$i",
        evaluationId = "eval-opt-70",
        questionNumber = "$i",
        questionText = "Question $i text",
        maximumMarks = 10.0,
        studentAnswer = if (attempted) "Verbatim student answer for Q$i" else "[Optional question — not attempted]",
        expectedAnswer = "[Provisional Reference Answer]: Reference $i",
        markingCriteria = "[Selection Rule: group=EXAM_OPTIONAL | attempt=7/10]\n[Provisional Criteria]: - Key point: 10 marks",
        awardedMarks = if (attempted) 8.0 else 0.0,
        feedback = if (attempted) "Correct." else "Optional question not attempted by student (7 of 10 required attempts satisfied).",
        reviewRequired = false,
        reviewReason = null,
      )
    }

    val effectiveTotal7Of10 = calculateEffectiveQuestionTotalMarks(dbQuestions7Of10)
    assertEquals(70.0, effectiveTotal7Of10, 0.001)

    // 2. Internal OR choice: Q1 (10 marks), Q2(a) (15 marks) OR Q2(b) (15 marks), Q3 (25 marks) -> effective total = 50 marks
    val orChoiceQuestions = listOf(
      EvaluationQuestion(
        questionNumber = "1",
        questionText = "Compulsory Q1",
        maximumMarks = 10.0,
        studentAnswer = "Q1 answer",
        markingCriteria = "[Provisional Criteria]: 10 marks",
        awardedMarks = 9.0,
        reviewRequired = false,
      ),
      EvaluationQuestion(
        questionNumber = "2(a)",
        questionText = "Option 2(a)",
        maximumMarks = 15.0,
        studentAnswer = "Q2(a) answer",
        markingCriteria = "[Selection Rule: group=OR_Q2 | attempt=1/2 | type=or]\n[Provisional Criteria]: 15 marks",
        awardedMarks = 13.0,
        reviewRequired = false,
      ),
      EvaluationQuestion(
        questionNumber = "2(b)",
        questionText = "Option 2(b)",
        maximumMarks = 15.0,
        studentAnswer = "[Optional question — not attempted]",
        markingCriteria = "[Selection Rule: group=OR_Q2 | attempt=1/2 | type=or]\n[Provisional Criteria]: 15 marks",
        awardedMarks = 0.0,
        reviewRequired = false,
      ),
      EvaluationQuestion(
        questionNumber = "3",
        questionText = "Compulsory Q3",
        maximumMarks = 25.0,
        studentAnswer = "Q3 answer",
        markingCriteria = "[Provisional Criteria]: 25 marks",
        awardedMarks = 22.0,
        reviewRequired = false,
      ),
    )

    val effectiveTotalOrChoice = calculateEffectiveEvaluationQuestionTotalMarks(orChoiceQuestions)
    assertEquals(50.0, effectiveTotalOrChoice, 0.001)
  }

  @Test
  fun testCrossPageQ4Q5SeparationAndAll12RegressionScenarios() {
    // Verify Kotlin parsing of cross-page Q4 continuation + Q5 on page 2 with source_pages
    val json = """
      {
        "status": "completed",
        "percentageAvailable": true,
        "evaluation": {
          "id": "eval-q4-q5-page2",
          "paperId": "paper-q4-q5-page2",
          "studentId": "student-1",
          "classId": "class-1",
          "teacherId": "teacher-1",
          "status": "completed",
          "totalMarksObtained": 19,
          "totalMarks": 20,
          "extractedQuestionTotalMarks": 20,
          "percentage": 95.0,
          "percentageAvailable": true,
          "reviewRequired": false,
          "reviewReason": null,
          "questions": [
            {
              "question_number": "4",
              "question_text": "Explain the causes and impact of the French Revolution.",
              "maximum_marks": 10,
              "student_answer": "The French Revolution began in 1789 due to severe financial crisis and social inequality.\n\nIt led to the abolition of feudalism and the Declaration of the Rights of Man.",
              "expected_answer": "[Provisional Reference Answer]: French Revolution...",
              "marking_criteria": "[Provisional Criteria]: 10 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 9,
              "feedback": "Comprehensive two-page answer.",
              "review_required": false,
              "review_reason": null,
              "source_pages": [1, 2]
            },
            {
              "question_number": "5",
              "question_text": "Explain photosynthesis.",
              "maximum_marks": 10,
              "student_answer": "Photosynthesis converts light energy into chemical energy in chloroplasts.",
              "expected_answer": "[Provisional Reference Answer]: Photosynthesis...",
              "marking_criteria": "[Provisional Criteria]: 10 marks",
              "expected_answer_source": "ai_generated",
              "marking_criteria_source": "ai_generated",
              "awarded_marks": 10,
              "feedback": "Accurate answer.",
              "review_required": false,
              "review_reason": null,
              "source_pages": [2]
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = parseAiEvaluationResponse(json)
    assertNotNull(parsed)
    val eval = parsed!!.evaluation!!
    val q4 = eval.questions.first { it.questionNumber == "4" }
    val q5 = eval.questions.first { it.questionNumber == "5" }

    assertEquals(
      "The French Revolution began in 1789 due to severe financial crisis and social inequality.\n\nIt led to the abolition of feudalism and the Declaration of the Rights of Man.",
      q4.studentAnswer
    )
    assertFalse(q4.studentAnswer!!.contains("Photosynthesis"))
    assertEquals(
      "Photosynthesis converts light energy into chemical energy in chloroplasts.",
      q5.studentAnswer
    )
    assertFalse(q5.studentAnswer!!.startsWith("Q5"))
    assertFalse(q5.studentAnswer!!.startsWith("Ans 5"))
    assertFalse(q5.studentAnswer!!.startsWith("Answer 5"))
  }

  @Test
  fun testSixQuestionsAttemptAnyThreeOutOfOrderAndOverAttemptPreservation() {
    val sixQuestionsAttempt3 = (1..6).map { num ->
      val attempted = num in listOf(3, 1, 4)
      EvaluationQuestion(
        id = "eq-$num",
        evaluationId = "eval-attempt-3-of-6",
        questionNumber = "$num",
        questionText = "Question $num (10 marks)",
        maximumMarks = 10.0,
        studentAnswer = if (attempted) "Student answer for Q$num" else "[Optional question — not attempted]",
        markingCriteria = "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] [Provisional Criteria]: 10 marks",
        awardedMarks = if (attempted) 9.0 else 0.0,
        reviewRequired = false,
      )
    }

    val effectiveTotal = calculateEffectiveEvaluationQuestionTotalMarks(sixQuestionsAttempt3)
    assertEquals(30.0, effectiveTotal, 0.001)
    assertEquals(27.0, sixQuestionsAttempt3.sumOf { it.awardedMarks ?: 0.0 }, 0.001)

    // Verify over-attempt (4 answered out of 3 allowed) preserves all 4 answers and their awarded marks while flagging for review
    val overAttemptJson = """
      {
        "status": "needs_review",
        "percentageAvailable": false,
        "evaluation": {
          "id": "eval-over-attempt-4-of-3",
          "paperId": "paper-over-attempt",
          "studentId": "student-1",
          "classId": "class-1",
          "teacherId": "teacher-1",
          "status": "needs_review",
          "totalMarksObtained": 0,
          "totalMarks": 30,
          "extractedQuestionTotalMarks": 30,
          "percentage": null,
          "percentageAvailable": false,
          "reviewRequired": true,
          "reviewReason": "Student attempted 4 questions in optional group 'EXAM_OPTIONAL' (attempt any 3 of 6) where only 3 are allowed; all detected answers are preserved and flagged for teacher review.",
          "questions": [
            {
              "question_number": "1",
              "question_text": "Q1",
              "maximum_marks": 10,
              "student_answer": "Answer for Q1",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 9,
              "review_required": true,
              "source_pages": [1]
            },
            {
              "question_number": "2",
              "question_text": "Q2",
              "maximum_marks": 10,
              "student_answer": "[Optional question — not attempted]",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 0,
              "review_required": false,
              "source_pages": []
            },
            {
              "question_number": "3",
              "question_text": "Q3",
              "maximum_marks": 10,
              "student_answer": "Answer for Q3",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 6,
              "review_required": true,
              "source_pages": [1]
            },
            {
              "question_number": "4",
              "question_text": "Q4",
              "maximum_marks": 10,
              "student_answer": "Answer for Q4",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 8,
              "review_required": true,
              "source_pages": [2]
            },
            {
              "question_number": "5",
              "question_text": "Q5",
              "maximum_marks": 10,
              "student_answer": "[Optional question — not attempted]",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 0,
              "review_required": false,
              "source_pages": []
            },
            {
              "question_number": "6",
              "question_text": "Q6",
              "maximum_marks": 10,
              "student_answer": "Answer for Q6",
              "marking_criteria": "[Selection Rule: group=EXAM_OPTIONAL; attempt=3/6] Criteria",
              "awarded_marks": 7,
              "review_required": true,
              "source_pages": [2]
            }
          ]
        }
      }
    """.trimIndent()

    val parsedOverAttempt = parseAiEvaluationResponse(overAttemptJson)
    assertNotNull(parsedOverAttempt)
    val evalOver = parsedOverAttempt!!.evaluation!!
    assertEquals(30.0, evalOver.totalMarks!!, 0.001)
    assertEquals(true, evalOver.reviewRequired)
    val q1 = evalOver.questions.first { it.questionNumber == "1" }
    val q3 = evalOver.questions.first { it.questionNumber == "3" }
    val q4 = evalOver.questions.first { it.questionNumber == "4" }
    val q6 = evalOver.questions.first { it.questionNumber == "6" }
    assertEquals("Answer for Q1", q1.studentAnswer)
    assertEquals(9.0, q1.awardedMarks!!, 0.001)
    assertEquals("Answer for Q3", q3.studentAnswer)
    assertEquals(6.0, q3.awardedMarks!!, 0.001)
    assertEquals("Answer for Q4", q4.studentAnswer)
    assertEquals(8.0, q4.awardedMarks!!, 0.001)
    assertEquals("Answer for Q6", q6.studentAnswer)
    assertEquals(7.0, q6.awardedMarks!!, 0.001)
  }

  @Test
  fun testNumericalQuestionOrderingAcrossParserAndViewModel() {
    val scrambledJson = """
      {
        "status": "completed",
        "percentageAvailable": true,
        "evaluation": {
          "id": "eval-order-1",
          "paperId": "paper-order-1",
          "studentId": "student-1",
          "classId": "class-1",
          "teacherId": "teacher-1",
          "status": "completed",
          "totalMarksObtained": 28,
          "totalMarks": 30,
          "percentage": 93.33,
          "percentageAvailable": true,
          "reviewRequired": false,
          "questions": [
            {
              "id": "q-10",
              "question_number": "10",
              "question_text": "Prompt 10",
              "maximum_marks": 10,
              "student_answer": "Student answer for Q10",
              "awarded_marks": 9,
              "feedback": "Feedback 10"
            },
            {
              "id": "q-2b",
              "question_number": "2(b)",
              "question_text": "Prompt 2b",
              "maximum_marks": 5,
              "student_answer": "Student answer for Q2(b)",
              "awarded_marks": 5,
              "feedback": "Feedback 2b"
            },
            {
              "id": "q-1",
              "question_number": "1",
              "question_text": "Prompt 1",
              "maximum_marks": 5,
              "student_answer": "Student answer for Q1",
              "awarded_marks": 5,
              "feedback": "Feedback 1"
            },
            {
              "id": "q-3",
              "question_number": "3",
              "question_text": "Prompt 3",
              "maximum_marks": 5,
              "student_answer": "Student answer for Q3",
              "awarded_marks": 4,
              "feedback": "Feedback 3"
            },
            {
              "id": "q-2a",
              "question_number": "2(a)",
              "question_text": "Prompt 2a",
              "maximum_marks": 5,
              "student_answer": "Student answer for Q2(a)",
              "awarded_marks": 5,
              "feedback": "Feedback 2a"
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = parseAiEvaluationResponse(scrambledJson)
    assertNotNull(parsed)
    val ordered = parsed!!.evaluation!!.questions
    assertEquals(listOf("1", "2(a)", "2(b)", "3", "10"), ordered.map { it.questionNumber })
    // Verify each question kept its exact student answer, marks, and feedback
    assertEquals("Student answer for Q1", ordered[0].studentAnswer)
    assertEquals(5.0, ordered[0].awardedMarks!!, 0.001)
    assertEquals("Student answer for Q2(a)", ordered[1].studentAnswer)
    assertEquals(5.0, ordered[1].awardedMarks!!, 0.001)
    assertEquals("Student answer for Q2(b)", ordered[2].studentAnswer)
    assertEquals(5.0, ordered[2].awardedMarks!!, 0.001)
    assertEquals("Student answer for Q3", ordered[3].studentAnswer)
    assertEquals(4.0, ordered[3].awardedMarks!!, 0.001)
    assertEquals("Student answer for Q10", ordered[4].studentAnswer)
    assertEquals(9.0, ordered[4].awardedMarks!!, 0.001)

    // Also verify Roman subparts (i) < (ii) < (iii) < (iv) < (v)
    assertTrue(compareQuestionNumbers("2(i)", "2(ii)") < 0)
    assertTrue(compareQuestionNumbers("2(iv)", "2(v)") < 0)
    assertTrue(compareQuestionNumbers("2", "10") < 0)
  }

  @Test
  fun testMarksEditingAndOptionalPaperTotalValidation() {
    // 6 questions x 10 marks each, attempt any 3, configured paper total = 30
    val criteriaTag = "[Selection Rule: group=EXAM_OPTIONAL | attempt=3/6 | type=optional] Award up to 10 marks"
    val initialQuestions = listOf(
      EvaluationQuestion(
        id = "q1",
        questionNumber = "1",
        questionText = "Question 1",
        maximumMarks = 10.0,
        studentAnswer = "Student answer for Q1",
        markingCriteria = criteriaTag,
        awardedMarks = 10.0,
        feedback = "Full marks Q1",
      ),
      EvaluationQuestion(
        id = "q2",
        questionNumber = "2",
        questionText = "Question 2",
        maximumMarks = 10.0,
        studentAnswer = "[Optional question — not attempted]",
        markingCriteria = criteriaTag,
        awardedMarks = 0.0,
        feedback = "Optional question not attempted",
      ),
      EvaluationQuestion(
        id = "q3",
        questionNumber = "3",
        questionText = "Question 3",
        maximumMarks = 10.0,
        studentAnswer = "Student answer for Q3",
        markingCriteria = criteriaTag,
        awardedMarks = 10.0,
        feedback = "Full marks Q3",
      ),
      EvaluationQuestion(
        id = "q4",
        questionNumber = "4",
        questionText = "Question 4",
        maximumMarks = 10.0,
        studentAnswer = "Student answer for Q4",
        markingCriteria = criteriaTag,
        awardedMarks = 8.0,
        feedback = "Good Q4",
      ),
      EvaluationQuestion(
        id = "q5",
        questionNumber = "5",
        questionText = "Question 5",
        maximumMarks = 10.0,
        studentAnswer = "[No answer detected]",
        markingCriteria = criteriaTag,
        awardedMarks = 0.0,
        feedback = "No answer",
      ),
      EvaluationQuestion(
        id = "q6",
        questionNumber = "6",
        questionText = "Question 6",
        maximumMarks = 10.0,
        studentAnswer = "Extra student answer for Q6",
        markingCriteria = criteriaTag,
        awardedMarks = 0.0,
        feedback = "Extra attempt",
      ),
    )

    val viewModel = StudentDetailViewModel(
      studentRepository = FakeStudentRepository(),
      classRepository = FakeClassRepository(),
      paperRepository = FakePaperRepository(),
      ocrRepository = FakeOcrRepository(),
    )

    val evalResponse = AiEvaluationResponse(
      status = "needs_review",
      percentageAvailable = true,
      evaluation = EvaluationDetail(
        id = "eval-opt-30",
        paperId = "paper-opt-30",
        studentId = "s-1",
        classId = "c-1",
        teacherId = "t-1",
        status = "needs_review",
        totalMarksObtained = 28.0,
        totalMarks = 30.0,
        extractedQuestionTotalMarks = 30.0,
        percentage = 93.33,
        percentageAvailable = true,
        reviewRequired = false,
        questions = initialQuestions,
      ),
    )

    viewModel.setEvaluationResultForTesting("paper-opt-30", evalResponse)

    // 1. Valid edit within [0, maximum_marks] and within paper total (30): change Q4 from 8.0 to 10.0 -> total = 30.0
    viewModel.onUpdateQuestion("q4", "4", 10.0, "Updated Q4")
    val afterValidEdit = viewModel.uiState.value
    assertNull(afterValidEdit.saveCorrectionsError)
    val q4Updated = afterValidEdit.editedQuestions!!.first { it.questionNumber == "4" }
    assertEquals(10.0, q4Updated.awardedMarks!!, 0.001)
    assertEquals(
      30.0,
      calculateCountedEvaluationTotalMarks(afterValidEdit.editedQuestions!!, 30.0),
      0.001
    )

    // 2. Reject mark exceeding question's maximum_marks (e.g. 12 on a 10-mark question) without overwriting Q4
    viewModel.onUpdateQuestion("q4", "4", 12.0, "Updated Q4")
    val afterExceedMax = viewModel.uiState.value
    assertNotNull(afterExceedMax.saveCorrectionsError)
    assertTrue(afterExceedMax.saveCorrectionsError!!.contains("cannot exceed its maximum of 10"))
    assertEquals(
      10.0,
      afterExceedMax.editedQuestions!!.first { it.questionNumber == "4" }.awardedMarks!!,
      0.001
    )

    // 3. Reject negative mark
    viewModel.onUpdateQuestion("q4", "4", -2.0, "Updated Q4")
    val afterNegative = viewModel.uiState.value
    assertNotNull(afterNegative.saveCorrectionsError)
    assertTrue(afterNegative.saveCorrectionsError!!.contains("cannot be negative"))
    assertEquals(
      10.0,
      afterNegative.editedQuestions!!.first { it.questionNumber == "4" }.awardedMarks!!,
      0.001
    )

    // 4. Reject awarding marks to unattempted question ("[Optional question — not attempted]" or "[No answer detected]")
    viewModel.onUpdateQuestion("q2", "2", 5.0, "Optional question not attempted")
    val afterUnattempted = viewModel.uiState.value
    assertNotNull(afterUnattempted.saveCorrectionsError)
    assertTrue(afterUnattempted.saveCorrectionsError!!.contains("no student answer was detected"))
    assertEquals(
      0.0,
      afterUnattempted.editedQuestions!!.first { it.questionNumber == "2" }.awardedMarks!!,
      0.001
    )

    // 5. Reject awarding marks to a 4th question (Q6) when 3 of 6 are already counted and paper total is 30
    viewModel.onUpdateQuestion("q6", "6", 5.0, "Extra attempt")
    val afterFourthAttempt = viewModel.uiState.value
    assertNotNull(afterFourthAttempt.saveCorrectionsError)
    assertEquals(
      0.0,
      afterFourthAttempt.editedQuestions!!.first { it.questionNumber == "6" }.awardedMarks!!,
      0.001
    )
    assertEquals(
      30.0,
      calculateCountedEvaluationTotalMarks(afterFourthAttempt.editedQuestions!!, 30.0),
      0.001
    )

    // 6. Save validation also blocks saving if questions list has raw total > paper total (e.g. 40 > 30)
    val invalidSaveList = initialQuestions.map {
      if (it.questionNumber in listOf("1", "3", "4", "6")) it.copy(awardedMarks = 10.0) else it
    }
    val saveError = validatePaperEvaluationForSave(invalidSaveList, 30.0)
    assertNotNull(saveError)
  }

  @Test
  fun testOptionalGroupScoringAndApproveEvaluationValidationAndUnsavedEditsBlocking() = runTest {
    val criteriaTag = "[Selection Rule: group=EXAM_OPTIONAL | attempt=3/6 | type=optional] Award up to 10 marks"

    fun buildOptionalQuestion(num: Int, answer: String, awarded: Double): EvaluationQuestion {
      return EvaluationQuestion(
        id = "q-$num",
        questionNumber = "$num",
        questionText = "Question $num",
        maximumMarks = 10.0,
        studentAnswer = answer,
        markingCriteria = criteriaTag,
        awardedMarks = awarded,
        feedback = "Feedback Q$num",
      )
    }

    // 1. Optional-group scoring: 4 scores of 5 in an attempt-3-of-6 group count as 15, not 20
    val fourFivesQuestions = listOf(
      buildOptionalQuestion(1, "Answer 1", 5.0),
      buildOptionalQuestion(2, "Answer 2", 5.0),
      buildOptionalQuestion(3, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(4, "Answer 4", 5.0),
      buildOptionalQuestion(5, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(6, "Answer 6", 5.0),
    )
    assertEquals(
      15.0,
      calculateCountedEvaluationTotalMarks(fourFivesQuestions, 30.0),
      0.001,
    )

    // 2. Different scores: 8, 6, 7, 5 across 4 attempted questions count as 21 (8 + 7 + 6)
    val mixedFourQuestions = listOf(
      buildOptionalQuestion(1, "Answer 1", 8.0),
      buildOptionalQuestion(2, "Answer 2", 6.0),
      buildOptionalQuestion(3, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(4, "Answer 4", 7.0),
      buildOptionalQuestion(5, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(6, "Answer 6", 5.0),
    )
    assertEquals(
      21.0,
      calculateCountedEvaluationTotalMarks(mixedFourQuestions, 30.0),
      0.001,
    )

    val student = Student(
      id = "s-1",
      classId = "c-1",
      teacherId = "t-1",
      name = "Zaid Ali",
      fatherName = "Ali",
      rollNumber = "01",
    )
    val classRoom = ClassRoom(
      id = "c-1",
      examId = "e-1",
      teacherId = "t-1",
      session = "2022-2026",
      semester = "4th",
      timing = "Morning",
      subjectName = "Physics",
      totalMarks = 30,
    )
    val paper = StudentPaper(
      id = "paper-opt-30",
      studentId = "s-1",
      classId = "c-1",
      teacherId = "t-1",
      totalPages = 1,
      status = "needs_review",
      evaluation = EvaluationStatus(status = "needs_review"),
    )

    val rpcCalls = mutableListOf<Pair<String, String>>()
    val viewModel = StudentDetailViewModel(
      studentRepository = FakeStudentRepository(listOf(student)),
      classRepository = FakeClassRepository(listOf(classRoom)),
      paperRepository = FakePaperRepository(listOf(paper)),
      ocrRepository = FakeOcrRepository(),
      approveEvaluationAction = { evalId, pId ->
        rpcCalls.add(evalId to pId)
        Result.success(Unit)
      },
    )
    viewModel.loadStudentAndPapers("s-1")

    val validQuestions = listOf(
      buildOptionalQuestion(1, "Answer 1", 8.0),
      buildOptionalQuestion(2, "Answer 2", 6.0),
      buildOptionalQuestion(3, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(4, "Answer 4", 7.0),
      buildOptionalQuestion(5, "[Optional question — not attempted]", 0.0),
      buildOptionalQuestion(6, "Answer 6 (extra attempt zeroed)", 0.0),
    )

    fun makeEvalResponse(
      questions: List<EvaluationQuestion>,
      totalObtained: Double = 21.0,
    ): AiEvaluationResponse {
      return AiEvaluationResponse(
        status = "needs_review",
        percentageAvailable = true,
        evaluation = EvaluationDetail(
          id = "eval-opt-30",
          paperId = "paper-opt-30",
          studentId = "s-1",
          classId = "c-1",
          teacherId = "t-1",
          status = "needs_review",
          totalMarksObtained = totalObtained,
          totalMarks = 30.0,
          extractedQuestionTotalMarks = 30.0,
          percentage = 70.0,
          percentageAvailable = true,
          reviewRequired = true,
          questions = questions,
        ),
      )
    }

    // 3. The actual ViewModel approval path blocks approval when teacher edits are unsaved
    viewModel.setEvaluationResultForTesting("paper-opt-30", makeEvalResponse(validQuestions))
    viewModel.onUpdateQuestion("q-1", "1", 9.0, "Updated Q1 feedback")
    assertNotNull(viewModel.uiState.value.editedQuestions)
    assertNull(viewModel.uiState.value.saveCorrectionsError)

    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")
    assertTrue(rpcCalls.isEmpty())
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertFalse(viewModel.uiState.value.isApproving)
    assertEquals(
      "Please save your edited marks and feedback before approving the result.",
      viewModel.uiState.value.approvalError,
    )
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.status)

    // 4. Approval is blocked when marks violate the existing validation rules
    // 4a. Unresolved optional-group over-attempt (4 questions have positive marks > 0 in a 3-of-6 group)
    viewModel.setEvaluationResultForTesting("paper-opt-30", makeEvalResponse(fourFivesQuestions, totalObtained = 15.0))
    assertNull(viewModel.uiState.value.editedQuestions)
    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")
    assertTrue(rpcCalls.isEmpty())
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertNotNull(viewModel.uiState.value.approvalError)
    assertTrue(viewModel.uiState.value.approvalError!!.contains("Only 3 of 6 questions may receive marks"))

    // 4b. Question mark exceeding maximum_marks (12 > 10)
    val overMaxQuestions = validQuestions.map {
      if (it.questionNumber == "1") it.copy(awardedMarks = 12.0) else it
    }
    viewModel.setEvaluationResultForTesting("paper-opt-30", makeEvalResponse(overMaxQuestions, totalObtained = 25.0))
    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")
    assertTrue(rpcCalls.isEmpty())
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertNotNull(viewModel.uiState.value.approvalError)
    assertTrue(viewModel.uiState.value.approvalError!!.contains("cannot exceed its maximum of 10"))

    // 4c. Teacher attempted an invalid edit when no prior valid edits exist:
    // editedQuestions must remain null, actual awardedMarks must remain 8.0, saveCorrectionsError must be set, and approveEvaluation is blocked
    viewModel.setEvaluationResultForTesting("paper-opt-30", makeEvalResponse(validQuestions, totalObtained = 21.0))
    viewModel.onUpdateQuestion("q-1", "1", 15.0, "Invalid mark edit")
    assertNull(viewModel.uiState.value.editedQuestions)
    assertEquals(
      8.0,
      viewModel.uiState.value.evaluationResult?.evaluation?.questions?.first { it.questionNumber == "1" }?.awardedMarks!!,
      0.001,
    )
    assertNotNull(viewModel.uiState.value.saveCorrectionsError)
    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")
    assertTrue(rpcCalls.isEmpty())
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertNotNull(viewModel.uiState.value.approvalError)
    assertTrue(viewModel.uiState.value.approvalError!!.contains("cannot exceed its maximum of 10"))

    // 4d. When an earlier valid pending edit exists, a subsequent rejected edit preserves the earlier valid edit
    viewModel.clearSaveCorrectionsError()
    viewModel.clearApprovalError()
    viewModel.onUpdateQuestion("q-1", "1", 9.0, "Valid Q1 edit")
    assertNotNull(viewModel.uiState.value.editedQuestions)
    viewModel.onUpdateQuestion("q-1", "1", 15.0, "Rejected Q1 edit")
    assertNotNull(viewModel.uiState.value.editedQuestions)
    val preservedQ1 = viewModel.uiState.value.editedQuestions!!.first { it.questionNumber == "1" }
    assertEquals(9.0, preservedQ1.awardedMarks!!, 0.001)
    assertEquals("Valid Q1 edit", preservedQ1.feedback)
    assertNotNull(viewModel.uiState.value.saveCorrectionsError)

    // 5. Normal valid approval still follows the existing workflow (and after clearing a rejected-only edit with no valid pending edits, editedQuestions is null so approval proceeds)
    viewModel.setEvaluationResultForTesting("paper-opt-30", makeEvalResponse(validQuestions, totalObtained = 21.0))
    viewModel.onUpdateQuestion("q-1", "1", 15.0, "Rejected only edit")
    assertNull(viewModel.uiState.value.editedQuestions)
    viewModel.clearSaveCorrectionsError()
    assertNull(viewModel.uiState.value.editedQuestions)
    assertNull(viewModel.uiState.value.saveCorrectionsError)
    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")

    assertEquals(listOf("eval-opt-30" to "paper-opt-30"), rpcCalls)
    assertTrue(viewModel.uiState.value.approvalSuccess)
    assertFalse(viewModel.uiState.value.isApproving)
    assertNull(viewModel.uiState.value.approvalError)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.status)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(false, viewModel.uiState.value.evaluationResult?.evaluation?.reviewRequired)
    assertEquals("Result approved and saved successfully.", viewModel.uiState.value.feedbackMessage)

    // Approved result remains read-only and blocks duplicate approval RPC calls
    viewModel.onUpdateQuestion("q-1", "1", 2.0, "Attempted edit after approval")
    assertNull(viewModel.uiState.value.editedQuestions)
    viewModel.approveEvaluation("eval-opt-30", "paper-opt-30")
    assertEquals(1, rpcCalls.size)
    assertEquals("This result has already been approved.", viewModel.uiState.value.feedbackMessage)
  }

  @Test
  fun scanPaperViewModel_multiPhotoSession_gallerySelection_previewRemove_andUploadFlow() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakePaperRepo = FakePaperRepository()
    val student = Student(
      id = "student-1",
      classId = "class-1",
      teacherId = "user-123",
      name = "Zaid Ali",
      fatherName = "Ali",
      rollNumber = "CS-05",
    )
    val fakeStudentRepo = FakeStudentRepository(initialStudents = listOf(student))
    val viewModel = ScanPaperViewModel(
      paperRepository = fakePaperRepo,
      studentRepository = fakeStudentRepo,
    )

    viewModel.loadStudentInfo("student-1")
    assertEquals("Zaid Ali", viewModel.uiState.value.studentName)
    assertEquals("CS-05", viewModel.uiState.value.studentRollNumber)

    // Capture multiple photos in one camera session (pages stay as-is without auto-crop or enhancement)
    val cameraUri1 = Uri.parse("content://gradescan/camera/answer_page_1.jpg")
    val cameraUri2 = Uri.parse("content://gradescan/camera/answer_page_2.jpg")
    viewModel.addPage(cameraUri1)
    viewModel.addPage(cameraUri2)

    // Add additional pages from Gallery
    val galleryUri3 = Uri.parse("content://media/external/images/media/103")
    viewModel.addPages(listOf(galleryUri3))

    assertEquals(3, viewModel.uiState.value.pages.size)
    assertEquals(cameraUri1, viewModel.uiState.value.pages[0].uri)
    assertEquals(1, viewModel.uiState.value.pages[0].pageNumber)
    assertEquals(cameraUri2, viewModel.uiState.value.pages[1].uri)
    assertEquals(2, viewModel.uiState.value.pages[1].pageNumber)
    assertEquals(galleryUri3, viewModel.uiState.value.pages[2].uri)
    assertEquals(3, viewModel.uiState.value.pages[2].pageNumber)

    // Preview page 2 and verify removing page 2 clears preview and renumbers remaining pages
    val page2 = viewModel.uiState.value.pages[1]
    viewModel.setPreviewPage(page2)
    assertEquals(cameraUri2, viewModel.uiState.value.previewPageUri)

    viewModel.removePage(page2.id)
    assertNull(viewModel.uiState.value.previewPageUri)
    assertEquals(2, viewModel.uiState.value.pages.size)
    assertEquals(1, viewModel.uiState.value.pages[0].pageNumber)
    assertEquals(cameraUri1, viewModel.uiState.value.pages[0].uri)
    assertEquals(2, viewModel.uiState.value.pages[1].pageNumber)
    assertEquals(galleryUri3, viewModel.uiState.value.pages[1].uri)

    // Save/upload preserved pages in sequence
    var savedPaperId: String? = null
    viewModel.finishScan(context, "student-1", "class-1") { savedPaperId = it }
    assertNotNull(savedPaperId)
    assertEquals(listOf(cameraUri1, galleryUri3), fakePaperRepo.lastUploadedPageUris)
    assertTrue(viewModel.uiState.value.pages.isEmpty())
  }

  @Test
  fun `verify HomeViewModel loadUserData loads concurrently and deduplicates in-flight calls`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val exam1 = Exam(id = "exam-1", teacherId = "user-123", name = "Midterm Math", type = "Mid Term", date = "2026-10-01")
    val exam2 = Exam(id = "exam-2", teacherId = "user-123", name = "Final Physics", type = "Final", date = "2026-10-02")
    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "user-123", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Math", totalMarks = 100)
    val class2 = ClassRoom(id = "class-2", examId = "exam-2", teacherId = "user-123", session = "2026", semester = "Fall", timing = "Evening", subjectName = "Physics", totalMarks = 100)

    val fakeAuthRepo = FakeAuthRepository(signedInUser = "teacher@school.edu")
    val deferred1 = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    val fakeExamRepo = FakeExamRepository(initialExams = listOf(exam1, exam2), deferredExamsResult = deferred1)
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1, class2))

    // 1. Start initial load in init block; it suspends on deferred1
    val homeViewModel = HomeViewModel(fakeAuthRepo, fakeExamRepo, fakeClassRepo)
    mainLooper.idle()
    assertTrue(homeViewModel.uiState.value.isLoading)
    assertEquals(1, fakeExamRepo.getExamsCallCount)

    // Calling loadUserData(forceRefresh = false) while in-flight is deduplicated
    homeViewModel.loadUserData(forceRefresh = false)
    mainLooper.idle()
    assertEquals(1, fakeExamRepo.getExamsCallCount)

    // Complete deferred1 and verify counts are populated
    deferred1.complete(Result.success(listOf(exam1, exam2)))
    fakeExamRepo.deferredExamsResult = null
    mainLooper.idle()
    assertEquals("teacher@school.edu", homeViewModel.uiState.value.teacherEmail)
    assertEquals(2, homeViewModel.uiState.value.examCount)
    assertEquals(2, homeViewModel.uiState.value.classCount)
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(1, fakeExamRepo.getExamsCallCount)
    assertEquals(1, fakeClassRepo.getClassCountCallCount)
    assertEquals(0, fakeClassRepo.getClassesCallCount)

    // 2. Verify forceRefresh = true cancels an older suspended request and prevents older request from resetting state
    val deferredOld = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredOld
    fakeExamRepo.swallowCancellationIntoFailure = true // Simulate repository catching Exception and returning Result.failure(CancellationException)
    homeViewModel.loadUserData(forceRefresh = true)
    mainLooper.idle()
    assertTrue(homeViewModel.uiState.value.isLoading)
    assertEquals(2, fakeExamRepo.getExamsCallCount)

    // Start a newer forceRefresh while deferredOld is still suspended
    val deferredNew = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredNew
    homeViewModel.loadUserData(forceRefresh = true)
    mainLooper.idle()
    assertEquals(1, fakeExamRepo.cancelledRequestCount)
    // Newer request must still own isLoading = true and have no error from cancelled older request
    assertTrue(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(3, fakeExamRepo.getExamsCallCount)

    // Complete newer request with 1 exam
    deferredNew.complete(Result.success(listOf(exam1)))
    fakeExamRepo.deferredExamsResult = null
    fakeExamRepo.swallowCancellationIntoFailure = false
    mainLooper.idle()
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(1, homeViewModel.uiState.value.examCount)
    assertEquals(2, homeViewModel.uiState.value.classCount)

    // 3. Verify CancellationException (both direct and wrapped in Result.failure) does NOT set errorMessage
    fakeExamRepo.throwCancellation = true
    homeViewModel.loadUserData(forceRefresh = true)
    mainLooper.idle()
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)

    fakeExamRepo.swallowCancellationIntoFailure = true
    homeViewModel.loadUserData(forceRefresh = true)
    mainLooper.idle()
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)

    // 4. Verify subsequent refresh works after cancellation
    fakeExamRepo.throwCancellation = false
    fakeExamRepo.swallowCancellationIntoFailure = false
    homeViewModel.loadUserData()
    mainLooper.idle()
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(2, homeViewModel.uiState.value.examCount)

    // 5. Verify account switching during the suspended class-fetch stage discards stale dashboard counts
    val deferredClasses = kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>()
    fakeClassRepo.deferredClassesResult = deferredClasses
    homeViewModel.loadUserData(forceRefresh = true)
    mainLooper.idle()
    // Exam stage completed synchronously, now suspended inside getClassesByExamId
    assertTrue(homeViewModel.uiState.value.isLoading)

    // Switch authenticated user without starting a newer loadUserData() request
    fakeAuthRepo.customUserId = "user-switched"
    deferredClasses.complete(Result.success(listOf(class1)))
    fakeClassRepo.deferredClassesResult = null
    mainLooper.idle()
    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(0, homeViewModel.uiState.value.examCount)
    assertEquals(0, homeViewModel.uiState.value.classCount)
  }

  @Test
  fun `verify ExamViewModel loadExams request ownership cancellation and cross-account isolation`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val examA = Exam(id = "exam-A", teacherId = "user-A", name = "Midterm Math", type = "Mid Term", date = "2026-10-01")
    val examB1 = Exam(id = "exam-B1", teacherId = "user-B", name = "History Final", type = "Final", date = "2026-10-02")
    val examB2 = Exam(id = "exam-B2", teacherId = "user-B", name = "Biology Quiz", type = "Mid Term", date = "2026-10-03")
    val fakeExamRepo = FakeExamRepository(initialExams = listOf(examA))
    val fakeAuthRepo = FakeAuthRepository(signedInUser = null, customUserId = "user-A") // Not signed in yet

    // 1. Premature unauthenticated request is avoided
    val examViewModel = ExamViewModel(fakeExamRepo, fakeAuthRepo)
    mainLooper.idle()
    assertEquals(0, fakeExamRepo.getExamsCallCount)
    assertTrue(examViewModel.uiState.value.exams.isEmpty())
    assertNull(examViewModel.uiState.value.errorMessage)

    // 2. In-flight deduplication while suspended on CompletableDeferred
    fakeAuthRepo.signIn("teacherA@school.edu", "password123")
    val deferredInFlight = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredInFlight
    examViewModel.loadExams()
    mainLooper.idle()
    assertTrue(examViewModel.uiState.value.isLoading)
    assertEquals(1, fakeExamRepo.getExamsCallCount)

    // Duplicate non-forced call while suspended does not launch a second repository call
    examViewModel.loadExams(forceRefresh = false)
    mainLooper.idle()
    assertEquals(1, fakeExamRepo.getExamsCallCount)

    // Complete first request
    deferredInFlight.complete(Result.success(listOf(examA)))
    fakeExamRepo.deferredExamsResult = null
    mainLooper.idle()
    assertEquals(1, examViewModel.uiState.value.exams.size)
    assertEquals("exam-A", examViewModel.uiState.value.exams.first().id)
    assertFalse(examViewModel.uiState.value.isLoading)

    // 3. Account switching while Teacher A's request is suspended (even when repository wraps CancellationException in Result.failure):
    // Start a suspended refresh for Teacher A
    val deferredTeacherA = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredTeacherA
    fakeExamRepo.swallowCancellationIntoFailure = true
    examViewModel.loadExams(forceRefresh = true)
    mainLooper.idle()
    assertTrue(examViewModel.uiState.value.isLoading)

    // Teacher A logs out (clearExams cancels deferredTeacherA) and Teacher B logs in and starts loading
    examViewModel.clearExams()
    mainLooper.idle()
    assertEquals(1, fakeExamRepo.cancelledRequestCount)
    assertFalse(examViewModel.uiState.value.isLoading)
    assertTrue(examViewModel.uiState.value.exams.isEmpty())
    assertNull(examViewModel.uiState.value.errorMessage)

    // Teacher B signs in and starts a suspended load
    fakeAuthRepo.customUserId = "user-B"
    fakeAuthRepo.signIn("teacherB@school.edu", "password123")
    val deferredTeacherB = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredTeacherB
    examViewModel.loadExams()
    mainLooper.idle()
    // Teacher B's request must still be loading and untainted by Teacher A's cancelled request
    assertTrue(examViewModel.uiState.value.isLoading)
    assertNull(examViewModel.uiState.value.errorMessage)

    // Complete Teacher B's request
    deferredTeacherB.complete(Result.success(listOf(examB1, examB2)))
    fakeExamRepo.deferredExamsResult = null
    fakeExamRepo.swallowCancellationIntoFailure = false
    mainLooper.idle()
    assertFalse(examViewModel.uiState.value.isLoading)
    assertEquals(2, examViewModel.uiState.value.exams.size)
    assertEquals("exam-B1", examViewModel.uiState.value.exams[0].id)

    // 4a. Genuinely suspended account-mismatch branch (without starting a newer request or incrementing requestGeneration):
    // Start a request for Teacher B and suspend it inside the repository
    val deferredAccountMismatch = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredAccountMismatch
    examViewModel.loadExams(forceRefresh = true)
    mainLooper.idle()
    assertTrue(examViewModel.uiState.value.isLoading)

    // Change authenticated user to Teacher C WITHOUT calling loadExams() or clearExams()
    fakeAuthRepo.customUserId = "user-C"
    // Complete Teacher B's suspended request while myGeneration == requestGeneration
    deferredAccountMismatch.complete(Result.success(listOf(examB1, examB2)))
    fakeExamRepo.deferredExamsResult = null
    mainLooper.idle()
    // Verify Teacher B's data is discarded by the user-ID mismatch check (ExamViewModel.kt:97-108)
    assertFalse(examViewModel.uiState.value.isLoading)
    assertTrue(examViewModel.uiState.value.exams.isEmpty())
    assertNull(examViewModel.uiState.value.selectedExam)
    assertNull(examViewModel.uiState.value.errorMessage)

    // 4b. Force-refresh replacement across user switch:
    val deferredStale = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredStale
    examViewModel.loadExams(forceRefresh = true)
    mainLooper.idle()
    assertTrue(examViewModel.uiState.value.isLoading)

    val examC = Exam(id = "exam-C", teacherId = "user-C", name = "Chemistry Lab", type = "Final", date = "2026-10-04")
    val deferredTeacherC = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    fakeExamRepo.deferredExamsResult = deferredTeacherC
    examViewModel.loadExams(forceRefresh = true)
    mainLooper.idle()
    assertEquals(2, fakeExamRepo.cancelledRequestCount)

    deferredTeacherC.complete(Result.success(listOf(examC)))
    fakeExamRepo.deferredExamsResult = null
    mainLooper.idle()
    assertFalse(examViewModel.uiState.value.isLoading)
    assertEquals(1, examViewModel.uiState.value.exams.size)
    assertEquals("exam-C", examViewModel.uiState.value.exams.first().id)

    // 5. Verify HomeViewModel.signOut failure when still authenticated, cancellation, and cleanup resilience:
    val homeViewModel = HomeViewModel(
      authRepository = fakeAuthRepo,
      examRepository = fakeExamRepo,
      onClearExamState = {
        examViewModel.clearExams()
        throw RuntimeException("Simulated cleanup exception")
      },
    )
    mainLooper.idle()
    assertEquals(1, homeViewModel.uiState.value.examCount)

    // 5a. When signOut fails and user is still authenticated, keep user on screen, show error, reset isSigningOut
    fakeAuthRepo.shouldFailSignOut = true
    var signedOutCallbackInvoked = false
    homeViewModel.signOut {
      signedOutCallbackInvoked = true
    }
    mainLooper.idle()
    assertFalse(signedOutCallbackInvoked)
    assertFalse(homeViewModel.uiState.value.isSigningOut)
    assertTrue(fakeAuthRepo.isUserSignedIn())
    assertEquals("Sign out network error", homeViewModel.uiState.value.errorMessage)
    assertEquals(1, homeViewModel.uiState.value.examCount)
    assertEquals(1, examViewModel.uiState.value.exams.size)

    // 5b. When signOut throws CancellationException, do not navigate to Login or show error
    fakeAuthRepo.shouldFailSignOut = false
    fakeAuthRepo.throwSignOutCancellation = true
    homeViewModel.signOut {
      signedOutCallbackInvoked = true
    }
    mainLooper.idle()
    assertFalse(signedOutCallbackInvoked)
    assertFalse(homeViewModel.uiState.value.isSigningOut)
    assertTrue(fakeAuthRepo.isUserSignedIn())
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(1, examViewModel.uiState.value.exams.size)

    // 5c. When signOut succeeds, complete sign-out even if onClearExamState throws an exception
    fakeAuthRepo.throwSignOutCancellation = false
    homeViewModel.signOut {
      signedOutCallbackInvoked = true
    }
    mainLooper.idle()
    assertTrue(signedOutCallbackInvoked)
    assertFalse(homeViewModel.uiState.value.isSigningOut)
    assertFalse(fakeAuthRepo.isUserSignedIn())
    assertTrue(examViewModel.uiState.value.exams.isEmpty())
    assertEquals(0, homeViewModel.uiState.value.examCount)
    assertEquals(0, homeViewModel.uiState.value.classCount)
  }

  @Test
  fun `verify SupabaseClientProvider baseOkHttpClient and shared client configuration`() {
    val baseClient = com.example.data.remote.SupabaseClientProvider.baseOkHttpClient
    assertNotNull(baseClient)
    assertEquals(30_000, baseClient.connectTimeoutMillis)
    assertEquals(30_000, baseClient.readTimeoutMillis)
    assertEquals(30_000, baseClient.writeTimeoutMillis)

    // Verify derived clients share the exact same connectionPool and dispatcher
    val derivedOcrClient = baseClient.newBuilder()
      .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
      .build()
    org.junit.Assert.assertSame(baseClient.connectionPool, derivedOcrClient.connectionPool)
    org.junit.Assert.assertSame(baseClient.dispatcher, derivedOcrClient.dispatcher)
    assertEquals(45_000, derivedOcrClient.connectTimeoutMillis)

    val fakeAuthRepo = FakeAuthRepository(signedInUser = "teacher@school.edu")
    val ocrRepo = SupabaseOcrRepository(fakeAuthRepo)
    assertNotNull(ocrRepo)

    val studentRepo = SupabaseStudentRepository(fakeAuthRepo)
    assertNotNull(studentRepo)

    val fakeStudentRepo = FakeStudentRepository()
    val fakeClassRepo = FakeClassRepository()
    val fakePaperRepo = FakePaperRepository()
    val detailViewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)
    assertNotNull(detailViewModel)
  }

  @Test
  fun `verify Phase 3 StudentViewModel and StudentDetailViewModel concurrent loading and signed URL reuse`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val classRoom = ClassRoom(
      id = "class-1",
      examId = "exam-1",
      teacherId = "user-123",
      session = "2026",
      semester = "Fall",
      timing = "Morning",
      subjectName = "Physics",
      totalMarks = 100,
    )
    val student1 = Student(id = "student-1", classId = "class-1", teacherId = "user-123", name = "Bob", fatherName = "Robert", rollNumber = "02")
    val student2 = Student(id = "student-2", classId = "class-1", teacherId = "user-123", name = "Alice", fatherName = "Alan", rollNumber = "01")
    val page1 = StudentPaperPage(id = "page-1", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 1, storagePath = "user-123/class-1/student-1/paper-1/page_1.jpg")
    val paper1 = StudentPaper(id = "paper-1", studentId = "student-1", classId = "class-1", teacherId = "user-123", totalPages = 1, status = "scanned", pages = listOf(page1))

    // 1. OPT-06: StudentViewModel.loadClassAndStudents runs getClassById and getStudentsByClassId concurrently
    val deferredClass = kotlinx.coroutines.CompletableDeferred<Result<ClassRoom?>>()
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(classRoom), deferredClassByIdResult = deferredClass)
    val fakeStudentRepo = FakeStudentRepository(initialStudents = listOf(student1, student2))
    val studentViewModel = StudentViewModel(fakeStudentRepo, fakeClassRepo)

    studentViewModel.loadClassAndStudents("class-1")
    mainLooper.idle()
    // Even while getClassById is still suspended on deferredClass, getStudentsByClassId has already been invoked concurrently!
    assertEquals(1, fakeClassRepo.getClassByIdCallCount)
    assertEquals(1, fakeStudentRepo.getStudentsByClassIdCallCount)

    deferredClass.complete(Result.success(classRoom))
    fakeClassRepo.deferredClassByIdResult = null
    mainLooper.idle()
    assertFalse(studentViewModel.uiState.value.isLoading)
    assertEquals("Physics", studentViewModel.uiState.value.classRoom?.subjectName)
    assertEquals(listOf("01", "02"), studentViewModel.uiState.value.students.map { it.rollNumber })

    // 2. OPT-05: StudentDetailViewModel.loadStudentAndPapers runs student/class and paper requests concurrently,
    // and deduplicates/reuses in-flight and cached signed URLs
    val deferredStudent = kotlinx.coroutines.CompletableDeferred<Result<Student?>>()
    fakeStudentRepo.deferredStudentByIdResult = deferredStudent
    val deferredSignedUrl = kotlinx.coroutines.CompletableDeferred<Result<String>>()
    val fakePaperRepo = FakePaperRepository(initialPapers = listOf(paper1), deferredSignedUrlResult = deferredSignedUrl)
    val detailViewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, fakePaperRepo)

    detailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    // While getStudentById is still suspended on deferredStudent, getPapersByStudentId has already been called concurrently!
    assertEquals(1, fakeStudentRepo.getStudentByIdCallCount)
    assertEquals(1, fakePaperRepo.getPapersCallCount)

    deferredStudent.complete(Result.success(student1))
    fakeStudentRepo.deferredStudentByIdResult = null
    mainLooper.idle()
    assertFalse(detailViewModel.uiState.value.isLoading)
    assertEquals("Bob", detailViewModel.uiState.value.student?.name)
    assertEquals("Physics", detailViewModel.uiState.value.classRoom?.subjectName)
    assertEquals(1, detailViewModel.uiState.value.papers.size)

    // Preload of page1.storagePath is currently suspended on deferredSignedUrl (call count == 1).
    // Calling loadSignedUrlForPage again (as StudentDetailScreen LaunchedEffect does) while in-flight must reuse the in-flight request!
    assertEquals(1, fakePaperRepo.getSignedPageUrlCallCount)
    detailViewModel.loadSignedUrlForPage(page1.storagePath)
    mainLooper.idle()
    assertEquals(1, fakePaperRepo.getSignedPageUrlCallCount)

    // Complete signed URL request and verify subsequent calls reuse cached signedUrls map
    deferredSignedUrl.complete(Result.success("https://signed.url/${page1.storagePath}"))
    fakePaperRepo.deferredSignedUrlResult = null
    mainLooper.idle()
    assertEquals("https://signed.url/${page1.storagePath}", detailViewModel.uiState.value.signedUrls[page1.storagePath])

    detailViewModel.loadSignedUrlForPage(page1.storagePath)
    mainLooper.idle()
    assertEquals(1, fakePaperRepo.getSignedPageUrlCallCount)

    // 3. Signed-URL retry after failure:
    // Add a second page whose initial signed URL request fails, then verify a retry succeeds
    val page2 = StudentPaperPage(id = "page-2", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 2, storagePath = "user-123/class-1/student-1/paper-1/page_2.jpg")
    val paperWith2Pages = paper1.copy(totalPages = 2, pages = listOf(page1, page2))
    val retryPaperRepo = FakePaperRepository(initialPapers = listOf(paperWith2Pages))
    val retryDetailViewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, retryPaperRepo)
    retryDetailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    assertEquals(1, retryPaperRepo.getSignedPageUrlCallCount) // page1 preloaded

    val deferredFailedUrl = kotlinx.coroutines.CompletableDeferred<Result<String>>()
    retryPaperRepo.deferredSignedUrlResult = deferredFailedUrl
    retryDetailViewModel.loadSignedUrlForPage(page2.storagePath)
    mainLooper.idle()
    assertEquals(2, retryPaperRepo.getSignedPageUrlCallCount)

    deferredFailedUrl.complete(Result.failure(Exception("Temporary storage timeout")))
    retryPaperRepo.deferredSignedUrlResult = null
    mainLooper.idle()
    assertNull(retryDetailViewModel.uiState.value.signedUrls[page2.storagePath])

    // Retry for page2.storagePath must NOT be blocked by the previous failure
    retryDetailViewModel.loadSignedUrlForPage(page2.storagePath)
    mainLooper.idle()
    assertEquals(3, retryPaperRepo.getSignedPageUrlCallCount)
    assertEquals("https://signed.url/${page2.storagePath}", retryDetailViewModel.uiState.value.signedUrls[page2.storagePath])

    // 4. In-flight signed-URL request completing AFTER page deletion cannot restore the deleted URL:
    val page3 = StudentPaperPage(id = "page-3", paperId = "paper-1", studentId = "student-1", teacherId = "user-123", pageNumber = 3, storagePath = "user-123/class-1/student-1/paper-1/page_3.jpg")
    val paperWith3Pages = paper1.copy(totalPages = 3, pages = listOf(page1, page2, page3))
    val deleteRacePaperRepo = FakePaperRepository(
      initialPapers = listOf(paperWith3Pages),
      ignoreSignedUrlCancellation = true,
    )
    val deleteRaceViewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, deleteRacePaperRepo)
    deleteRaceViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()

    val deferredDeletedPageUrl = kotlinx.coroutines.CompletableDeferred<Result<String>>()
    deleteRacePaperRepo.deferredSignedUrlResult = deferredDeletedPageUrl
    deleteRaceViewModel.loadSignedUrlForPage(page3.storagePath)
    mainLooper.idle()
    assertNull(deleteRaceViewModel.uiState.value.signedUrls[page3.storagePath])

    // Delete page 3 while its signed-URL request is still suspended in-flight
    deleteRaceViewModel.deletePaperPage(page3)
    mainLooper.idle()
    assertEquals(2, deleteRaceViewModel.uiState.value.papers.first().pages.size)
    assertNull(deleteRaceViewModel.uiState.value.signedUrls[page3.storagePath])

    // Even when the in-flight request resolves with a URL after page deletion, it must NOT be added back to signedUrls
    deferredDeletedPageUrl.complete(Result.success("https://signed.url/${page3.storagePath}"))
    deleteRacePaperRepo.deferredSignedUrlResult = null
    mainLooper.idle()
    assertNull(deleteRaceViewModel.uiState.value.signedUrls[page3.storagePath])

    // 5. Stale overlapping loadStudentAndPapers cannot overwrite a newer student-detail result:
    val stalePaper = StudentPaper(id = "paper-stale", studentId = "student-1", classId = "class-1", teacherId = "user-123", totalPages = 1, status = "scanned", pages = listOf(page1))
    val freshPaper = StudentPaper(id = "paper-fresh", studentId = "student-1", classId = "class-1", teacherId = "user-123", totalPages = 2, status = "approved", pages = listOf(page1, page2))
    val overlapPaperRepo = FakePaperRepository(
      initialPapers = listOf(freshPaper),
      ignoreCancellationOnDeferred = true,
    )
    val overlapDetailViewModel = StudentDetailViewModel(fakeStudentRepo, fakeClassRepo, overlapPaperRepo)

    val deferredOldDetail = kotlinx.coroutines.CompletableDeferred<Result<List<StudentPaper>>>()
    overlapPaperRepo.deferredPapersResult = deferredOldDetail
    overlapDetailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    assertTrue(overlapDetailViewModel.uiState.value.isLoading)

    val deferredNewDetail = kotlinx.coroutines.CompletableDeferred<Result<List<StudentPaper>>>()
    overlapPaperRepo.deferredPapersResult = deferredNewDetail
    overlapDetailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    assertTrue(overlapDetailViewModel.uiState.value.isLoading)
    assertNull(overlapDetailViewModel.uiState.value.errorMessage)

    // Complete newer request first
    deferredNewDetail.complete(Result.success(listOf(freshPaper)))
    overlapPaperRepo.deferredPapersResult = null
    mainLooper.idle()
    assertFalse(overlapDetailViewModel.uiState.value.isLoading)
    assertEquals(listOf("paper-fresh"), overlapDetailViewModel.uiState.value.papers.map { it.id })

    // Now complete older request with stale data; it must NOT overwrite the newer papers list
    deferredOldDetail.complete(Result.success(listOf(stalePaper)))
    mainLooper.idle()
    assertFalse(overlapDetailViewModel.uiState.value.isLoading)
    assertNull(overlapDetailViewModel.uiState.value.errorMessage)
    assertEquals(listOf("paper-fresh"), overlapDetailViewModel.uiState.value.papers.map { it.id })

    // 6. Stale overlapping loadClassAndStudents cannot overwrite a newer class/student result:
    val overlapStudentRepo = FakeStudentRepository(
      initialStudents = listOf(student1, student2),
      ignoreCancellationOnDeferred = true,
    )
    val overlapStudentViewModel = StudentViewModel(overlapStudentRepo, fakeClassRepo)

    val deferredOldStudents = kotlinx.coroutines.CompletableDeferred<Result<List<Student>>>()
    overlapStudentRepo.deferredStudentsByClassIdResult = deferredOldStudents
    overlapStudentViewModel.loadClassAndStudents("class-1")
    mainLooper.idle()
    assertTrue(overlapStudentViewModel.uiState.value.isLoading)

    val deferredNewStudents = kotlinx.coroutines.CompletableDeferred<Result<List<Student>>>()
    overlapStudentRepo.deferredStudentsByClassIdResult = deferredNewStudents
    overlapStudentViewModel.loadClassAndStudents("class-1")
    mainLooper.idle()
    assertTrue(overlapStudentViewModel.uiState.value.isLoading)
    assertNull(overlapStudentViewModel.uiState.value.errorMessage)

    // Complete newer request first with both students
    deferredNewStudents.complete(Result.success(listOf(student2, student1)))
    overlapStudentRepo.deferredStudentsByClassIdResult = null
    mainLooper.idle()
    assertFalse(overlapStudentViewModel.uiState.value.isLoading)
    assertEquals(listOf("01", "02"), overlapStudentViewModel.uiState.value.students.map { it.rollNumber })

    // Now complete older request with only 1 stale student; it must NOT overwrite the newer student list
    deferredOldStudents.complete(Result.success(listOf(student1)))
    mainLooper.idle()
    assertFalse(overlapStudentViewModel.uiState.value.isLoading)
    assertNull(overlapStudentViewModel.uiState.value.errorMessage)
    assertEquals(listOf("01", "02"), overlapStudentViewModel.uiState.value.students.map { it.rollNumber })

    // 7. Cancellation (direct or wrapped in Result.failure) does not become a normal UI error in either ViewModel:
    overlapPaperRepo.ignoreCancellationOnDeferred = false
    overlapPaperRepo.throwCancellation = true
    overlapPaperRepo.swallowCancellationIntoFailure = false
    overlapDetailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    assertFalse(overlapDetailViewModel.uiState.value.isLoading)
    assertNull(overlapDetailViewModel.uiState.value.errorMessage)

    overlapPaperRepo.swallowCancellationIntoFailure = true
    overlapDetailViewModel.loadStudentAndPapers("student-1")
    mainLooper.idle()
    assertFalse(overlapDetailViewModel.uiState.value.isLoading)
    assertNull(overlapDetailViewModel.uiState.value.errorMessage)

    overlapStudentRepo.ignoreCancellationOnDeferred = false
    overlapStudentRepo.throwCancellation = true
    overlapStudentRepo.swallowCancellationIntoFailure = false
    overlapStudentViewModel.loadClassAndStudents("class-1")
    mainLooper.idle()
    assertFalse(overlapStudentViewModel.uiState.value.isLoading)
    assertNull(overlapStudentViewModel.uiState.value.errorMessage)

    overlapStudentRepo.swallowCancellationIntoFailure = true
    overlapStudentViewModel.loadClassAndStudents("class-1")
    mainLooper.idle()
    assertFalse(overlapStudentViewModel.uiState.value.isLoading)
    assertNull(overlapStudentViewModel.uiState.value.errorMessage)
  }

  @Test
  fun `verify StorageCleanupHelper deleteFromBucket detects silent RLS no-op delete and rethrows CancellationException`() = runTest {
    val objectsInStorage = mutableSetOf<String>()
    var silentRlsNoOpDelete = false
    val undeletablePaths = mutableSetOf<String>()
    var throwCancelOnDelete = false
    var throwCancelOnExists = false
    val deleteBatches = mutableListOf<List<String>>()
    val verifiedPaths = mutableListOf<String>()

    val fakeBucketApi = java.lang.reflect.Proxy.newProxyInstance(
      io.github.jan.supabase.storage.BucketApi::class.java.classLoader,
      arrayOf(io.github.jan.supabase.storage.BucketApi::class.java),
    ) { _, method, args ->
      when (method.name) {
        "delete" -> {
          if (throwCancelOnDelete) {
            throw kotlinx.coroutines.CancellationException("Cancelled during bucket.delete")
          }
          @Suppress("UNCHECKED_CAST")
          val pathsArg = (args?.firstOrNull() as? Collection<String>)?.toList().orEmpty()
          deleteBatches.add(pathsArg)
          if (!silentRlsNoOpDelete) {
            for (path in pathsArg) {
              if (path !in undeletablePaths) {
                objectsInStorage.remove(path)
              }
            }
          }
          // BucketApi.delete returns Unit even when Supabase Storage returns HTTP 200 [] under RLS block
          Unit
        }
        "exists" -> {
          if (throwCancelOnExists) {
            throw kotlinx.coroutines.CancellationException("Cancelled during bucket.exists")
          }
          val pathArg = args?.firstOrNull() as? String ?: ""
          verifiedPaths.add(pathArg)
          objectsInStorage.contains(pathArg)
        }
        else -> throw UnsupportedOperationException("Unexpected BucketApi call: ${method.name}")
      }
    } as io.github.jan.supabase.storage.BucketApi

    // 1. Silent RLS no-op delete: bucket.delete returns Unit without throwing, but object still exists in Storage
    val targetPath = "user-123/exam-1/question_paper.jpg"
    objectsInStorage.add(targetPath)
    silentRlsNoOpDelete = true

    val silentNoOpResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "question-papers",
        rawPaths = listOf(targetPath),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(silentNoOpResult.isFailure)
    val silentNoOpError = silentNoOpResult.exceptionOrNull()
    assertTrue(silentNoOpError is IllegalStateException)
    assertTrue(silentNoOpError?.message?.contains("question-papers") == true)
    assertTrue(silentNoOpError?.message?.contains(targetPath) == true)
    assertTrue(objectsInStorage.contains(targetPath))

    // 2. Partial silent failure in a multi-file batch: 1st file deleted, 2nd file blocked by RLS
    objectsInStorage.clear()
    deleteBatches.clear()
    verifiedPaths.clear()
    silentRlsNoOpDelete = false
    val page1Path = "user-123/class-1/student-1/paper-1/page_1.jpg"
    val page2Path = "user-123/class-1/student-1/paper-1/page_2.jpg"
    objectsInStorage.addAll(listOf(page1Path, page2Path))
    undeletablePaths.add(page2Path)

    val partialFailureResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = listOf(page1Path, page2Path),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(partialFailureResult.isFailure)
    assertTrue(partialFailureResult.exceptionOrNull() is IllegalStateException)
    assertTrue(partialFailureResult.exceptionOrNull()?.message?.contains(page2Path) == true)

    // 3. Successful deletion across >100 paths (batching, normalization, and post-delete verification)
    objectsInStorage.clear()
    undeletablePaths.clear()
    deleteBatches.clear()
    verifiedPaths.clear()
    val manyRawPaths = (1..105).map { idx ->
      "/student-papers/user-123/class-1/student-1/paper-1/page_$idx.jpg"
    }
    val expectedNormalized = (1..105).map { idx ->
      "user-123/class-1/student-1/paper-1/page_$idx.jpg"
    }
    objectsInStorage.addAll(expectedNormalized)

    val batchSuccessResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = manyRawPaths + listOf("  ", null, expectedNormalized.first()),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(batchSuccessResult.isSuccess)
    assertEquals(2, deleteBatches.size)
    assertEquals(100, deleteBatches[0].size)
    assertEquals(5, deleteBatches[1].size)
    assertEquals(expectedNormalized, verifiedPaths)
    assertTrue(objectsInStorage.isEmpty())

    // 4. CancellationException during bucket.delete is rethrown directly (not wrapped in IllegalStateException)
    throwCancelOnDelete = true
    val cancelDeleteResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = listOf(page1Path),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(cancelDeleteResult.exceptionOrNull() is kotlinx.coroutines.CancellationException)

    // 5. CancellationException during bucket.exists verification is rethrown directly (not wrapped in IllegalStateException)
    throwCancelOnDelete = false
    throwCancelOnExists = true
    val cancelExistsResult = runCatching {
      com.example.data.repository.StorageCleanupHelper.deleteFromBucket(
        bucketName = "student-papers",
        rawPaths = listOf(page1Path),
        bucketApi = fakeBucketApi,
      )
    }
    assertTrue(cancelExistsResult.exceptionOrNull() is kotlinx.coroutines.CancellationException)
  }

  @Test
  fun `verify Phase 4 loadExistingEvaluationForPaper distinguishes outcomes stops on lookup failure propagates cancellation and reuses saved evaluations`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    fun waitForCondition(condition: () -> Boolean) {
      val deadline = System.currentTimeMillis() + 5_000L
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (condition()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    val student = Student(
      id = "student-eval-1",
      classId = "class-eval-1",
      teacherId = "teacher-1",
      name = "Amina Khan",
      fatherName = "Tariq Khan",
      rollNumber = "12",
    )
    val classRoom = ClassRoom(
      id = "class-eval-1",
      examId = "exam-eval-1",
      teacherId = "teacher-1",
      session = "2026",
      semester = "Fall",
      timing = "Morning",
      subjectName = "Chemistry",
      totalMarks = 20,
    )
    val scannedPaper = StudentPaper(
      id = "paper-eval-1",
      studentId = "student-eval-1",
      classId = "class-eval-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "scanned",
    )

    fun makeDbEvaluation(
      paperId: String,
      status: String,
      reviewRequired: Boolean = status == "needs_review",
    ): Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>> {
      val evalRecord = com.example.data.model.EvaluationDbRecord(
        id = "eval-$paperId-$status",
        paperId = paperId,
        studentId = "student-eval-1",
        classId = "class-eval-1",
        teacherId = "teacher-1",
        status = status,
        totalMarksObtained = 16.0,
        totalMarks = 20.0,
        percentage = 80.0,
        reviewRequired = reviewRequired,
        reviewReason = if (reviewRequired) "Low confidence on Q2" else null,
      )
      val questions = listOf(
        com.example.data.model.QuestionDbRecord(
          id = "q-2",
          evaluationId = evalRecord.id,
          questionNumber = "2",
          questionText = "Explain covalent bonding.",
          maximumMarks = 10.0,
          studentAnswer = "Sharing of electron pairs",
          expectedAnswer = "Sharing of electrons between atoms",
          markingCriteria = "Full marks for electron sharing",
          awardedMarks = 8.0,
          feedback = "Good explanation",
        ),
        com.example.data.model.QuestionDbRecord(
          id = "q-1",
          evaluationId = evalRecord.id,
          questionNumber = "1",
          questionText = "Define atomic number.",
          maximumMarks = 10.0,
          studentAnswer = "Number of protons in nucleus",
          expectedAnswer = "Number of protons",
          markingCriteria = "10 marks for correct definition",
          awardedMarks = 8.0,
          feedback = "Correct",
        ),
      )
      return evalRecord to questions
    }

    var edgeFunctionCallCount = 0
    var lookupCallCount = 0
    var lookupMode: String = "not_found"
    var suspendedLookupDeferred: kotlinx.coroutines.CompletableDeferred<Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>>?>? = null

    val mockEdgeClient = okhttp3.OkHttpClient.Builder()
      .addInterceptor { chain ->
        edgeFunctionCallCount++
        val responseJson = """
          {
            "status": "completed",
            "percentageAvailable": true,
            "evaluation": {
              "id": "eval-from-edge",
              "paperId": "paper-eval-1",
              "studentId": "student-eval-1",
              "classId": "class-eval-1",
              "teacherId": "teacher-1",
              "status": "completed",
              "totalMarksObtained": 18.0,
              "totalMarks": 20.0,
              "percentage": 90.0,
              "reviewRequired": false,
              "questions": [
                {
                  "id": "q-edge-1",
                  "evaluation_id": "eval-from-edge",
                  "question_number": "1",
                  "question_text": "Define atomic number.",
                  "maximum_marks": 20.0,
                  "student_answer": "Number of protons",
                  "awarded_marks": 18.0,
                  "feedback": "Well done"
                }
              ]
            }
          }
        """.trimIndent()
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(with(okhttp3.ResponseBody) { responseJson.toResponseBody(null) })
          .build()
      }
      .build()

    val fakeStudentRepo = FakeStudentRepository(listOf(student))
    val fakeClassRepo = FakeClassRepository(listOf(classRoom))
    val fakePaperRepo = FakePaperRepository(listOf(scannedPaper))

    val viewModel = StudentDetailViewModel(
      studentRepository = fakeStudentRepo,
      classRepository = fakeClassRepo,
      paperRepository = fakePaperRepo,
      evaluationHttpClient = mockEdgeClient,
      existingEvaluationDbFetcher = { paperId ->
        lookupCallCount++
        suspendedLookupDeferred?.await()
        when (lookupMode) {
          "found_completed" -> makeDbEvaluation(paperId, "completed")
          "found_needs_review" -> makeDbEvaluation(paperId, "needs_review")
          "found_approved" -> makeDbEvaluation(paperId, "approved", reviewRequired = false)
          "not_found" -> null
          "db_failure" -> throw java.io.IOException("Database connection reset while querying student_paper_evaluations")
          "throw_cancellation" -> throw kotlinx.coroutines.CancellationException("Lookup coroutine cancelled")
          else -> null
        }
      },
      evaluationAccessTokenProvider = { "test-teacher-jwt-token" },
    )

    viewModel.loadStudentAndPapers("student-eval-1")
    mainLooper.idle()

    // 1. Direct unit verification of loadExistingEvaluationForPaper() outcomes:
    // 1a. Evaluation exists -> Found (with questions sorted numerically Q1, Q2)
    lookupMode = "found_completed"
    val foundOutcome = viewModel.loadExistingEvaluationForPaper("paper-eval-1")
    assertTrue(foundOutcome is StudentDetailViewModel.ExistingEvaluationLookupResult.Found)
    val foundResp = (foundOutcome as StudentDetailViewModel.ExistingEvaluationLookupResult.Found).response
    assertEquals("completed", foundResp.status)
    assertEquals(listOf("1", "2"), foundResp.evaluation?.questions?.map { it.questionNumber })

    // 1b. No evaluation exists -> NotFound
    lookupMode = "not_found"
    val notFoundOutcome = viewModel.loadExistingEvaluationForPaper("paper-eval-1")
    assertTrue(notFoundOutcome is StudentDetailViewModel.ExistingEvaluationLookupResult.NotFound)

    // 1c. Database lookup fails -> Failed (not NotFound)
    lookupMode = "db_failure"
    val failedOutcome = viewModel.loadExistingEvaluationForPaper("paper-eval-1")
    assertTrue(failedOutcome is StudentDetailViewModel.ExistingEvaluationLookupResult.Failed)
    assertTrue(
      (failedOutcome as StudentDetailViewModel.ExistingEvaluationLookupResult.Failed)
        .error.message?.contains("Database connection reset") == true
    )

    // 1d. CancellationException is rethrown directly (never converted into NotFound or Failed)
    lookupMode = "throw_cancellation"
    val cancelOutcome = runCatching {
      viewModel.loadExistingEvaluationForPaper("paper-eval-1")
    }
    assertTrue(cancelOutcome.exceptionOrNull() is kotlinx.coroutines.CancellationException)

    // 2. Database lookup failure during runAiEvaluation():
    // Must stop runAiEvaluation(), report an error, and MUST NOT call the AI Edge Function
    edgeFunctionCallCount = 0
    lookupCallCount = 0
    lookupMode = "db_failure"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && lookupCallCount == 1 }

    assertEquals(1, lookupCallCount)
    assertEquals(0, edgeFunctionCallCount)
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertNull(viewModel.uiState.value.evaluationResult)
    assertNull(viewModel.uiState.value.pendingReviewNavigationPaperId)
    assertNotNull(viewModel.uiState.value.evaluationError)
    assertTrue(viewModel.uiState.value.evaluationError!!.contains("Database connection reset"))

    // 3. Cancellation during existing-evaluation lookup:
    // 3a. Direct CancellationException from lookup propagates without calling AI Edge Function or setting evaluationError
    viewModel.dismissEvaluationDialog()
    edgeFunctionCallCount = 0
    lookupCallCount = 0
    lookupMode = "throw_cancellation"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && lookupCallCount == 1 }

    assertEquals(1, lookupCallCount)
    assertEquals(0, edgeFunctionCallCount)
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertNull(viewModel.uiState.value.evaluationError)
    assertNull(viewModel.uiState.value.evaluationResult)
    assertNull(viewModel.uiState.value.pendingReviewNavigationPaperId)

    // 3b. In-flight lookup cancelled via stopAiEvaluation() while suspended does not fall through to Edge Function
    edgeFunctionCallCount = 0
    lookupCallCount = 0
    lookupMode = "not_found"
    val inFlightLookup = kotlinx.coroutines.CompletableDeferred<Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>>?>()
    suspendedLookupDeferred = inFlightLookup
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { lookupCallCount == 1 }
    assertEquals("paper-eval-1", viewModel.uiState.value.isEvaluatingPaperId)

    viewModel.stopAiEvaluation("paper-eval-1")
    inFlightLookup.complete(null)
    suspendedLookupDeferred = null
    Thread.sleep(50)
    mainLooper.idle()

    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertNull(viewModel.uiState.value.evaluationError)
    assertEquals(0, edgeFunctionCallCount)

    // 4. Existing saved evaluations ('completed', 'needs_review', 'approved') are reused without calling Edge Function:
    // 4a. 'completed' saved evaluation reused
    viewModel.dismissEvaluationDialog()
    edgeFunctionCallCount = 0
    lookupCallCount = 0
    lookupMode = "found_completed"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals(1, lookupCallCount)
    assertEquals(0, edgeFunctionCallCount)
    assertEquals("completed", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals("paper-eval-1", viewModel.uiState.value.pendingReviewNavigationPaperId)
    assertNull(viewModel.uiState.value.evaluationError)

    // 4b. 'needs_review' saved evaluation reused
    viewModel.dismissEvaluationDialog()
    lookupCallCount = 0
    lookupMode = "found_needs_review"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals(1, lookupCallCount)
    assertEquals(0, edgeFunctionCallCount)
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals("paper-eval-1", viewModel.uiState.value.pendingReviewNavigationPaperId)

    // 4c. 'approved' saved evaluation reused and clears any editedQuestions
    viewModel.dismissEvaluationDialog()
    lookupCallCount = 0
    lookupMode = "found_approved"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals(1, lookupCallCount)
    assertEquals(0, edgeFunctionCallCount)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertNull(viewModel.uiState.value.editedQuestions)
    assertEquals("paper-eval-1", viewModel.uiState.value.pendingReviewNavigationPaperId)

    // 5. Genuinely missing evaluation (NotFound) proceeds with the normal AI Edge Function evaluation flow:
    viewModel.dismissEvaluationDialog()
    edgeFunctionCallCount = 0
    lookupCallCount = 0
    lookupMode = "not_found"
    viewModel.runAiEvaluation("paper-eval-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && edgeFunctionCallCount == 1 }

    assertEquals(1, lookupCallCount)
    assertEquals(1, edgeFunctionCallCount)
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertNull(viewModel.uiState.value.evaluationError)
    assertEquals("eval-from-edge", viewModel.uiState.value.evaluationResult?.evaluation?.id)
    assertEquals(18.0, viewModel.uiState.value.evaluationResult?.evaluation?.totalMarksObtained ?: 0.0, 0.001)
    assertEquals("paper-eval-1", viewModel.uiState.value.pendingReviewNavigationPaperId)
  }

  @Test
  fun `verify Phase 4 approval and saving safety guards cancellation status preservation and stale paper protection`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

    fun waitForCondition(timeoutMs: Long = 3000L, condition: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (condition()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    val student = Student(
      id = "s-safe-1",
      classId = "c-safe-1",
      teacherId = "t-safe-1",
      name = "Amina Khan",
      fatherName = "Tariq Khan",
      rollNumber = "12",
    )
    val classRoom = ClassRoom(
      id = "c-safe-1",
      examId = "e-safe-1",
      teacherId = "t-safe-1",
      session = "2024-2028",
      semester = "2nd",
      timing = "Morning",
      subjectName = "Chemistry",
      totalMarks = 20,
    )
    val paper1 = StudentPaper(
      id = "paper-safe-1",
      studentId = "s-safe-1",
      classId = "c-safe-1",
      teacherId = "t-safe-1",
      totalPages = 1,
      status = "needs_review",
      evaluation = EvaluationStatus(status = "needs_review"),
    )
    val paper2 = StudentPaper(
      id = "paper-safe-2",
      studentId = "s-safe-1",
      classId = "c-safe-1",
      teacherId = "t-safe-1",
      totalPages = 1,
      status = "needs_review",
      evaluation = EvaluationStatus(status = "needs_review"),
    )

    fun buildEvalResponse(
      evalId: String,
      paperId: String,
      status: String = "needs_review",
      reviewRequired: Boolean = true,
      q1Marks: Double = 8.0,
      q2Marks: Double = 7.0,
    ): AiEvaluationResponse {
      val totalObtained = q1Marks + q2Marks
      return AiEvaluationResponse(
        status = status,
        percentageAvailable = true,
        evaluation = EvaluationDetail(
          id = evalId,
          paperId = paperId,
          studentId = "s-safe-1",
          classId = "c-safe-1",
          teacherId = "t-safe-1",
          status = status,
          totalMarksObtained = totalObtained,
          totalMarks = 20.0,
          extractedQuestionTotalMarks = 20.0,
          percentage = (totalObtained / 20.0) * 100.0,
          percentageAvailable = true,
          reviewRequired = reviewRequired,
          questions = listOf(
            EvaluationQuestion(
              id = "$evalId-q1",
              evaluationId = evalId,
              questionNumber = "1",
              questionText = "Question 1",
              maximumMarks = 10.0,
              studentAnswer = "Answer 1",
              awardedMarks = q1Marks,
              feedback = "Feedback 1",
            ),
            EvaluationQuestion(
              id = "$evalId-q2",
              evaluationId = evalId,
              questionNumber = "2",
              questionText = "Question 2",
              maximumMarks = 10.0,
              studentAnswer = "Answer 2",
              awardedMarks = q2Marks,
              feedback = "Feedback 2",
            ),
          ),
        ),
      )
    }

    var approvalCallCount = 0
    val approvalCalls = mutableListOf<Pair<String, String>>()
    var pendingApprovalDeferred: kotlinx.coroutines.CompletableDeferred<Result<Unit>>? = null
    var throwCancelOnApprove = false

    var saveCallCount = 0
    val saveCalls = mutableListOf<Pair<String, String>>()
    var pendingSaveDeferred: kotlinx.coroutines.CompletableDeferred<Result<AiEvaluationResponse>>? = null
    var throwCancelOnSave = false

    var dbEvalStatus = "needs_review"
    var dbReviewRequired = true
    var pendingDbLookupDeferred: kotlinx.coroutines.CompletableDeferred<Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>>?>? = null

    val fakeStudentRepository = FakeStudentRepository(listOf(student))
    val fakeClassRepository = FakeClassRepository(listOf(classRoom))
    val fakePaperRepository = FakePaperRepository(listOf(paper1, paper2))

    val viewModel = StudentDetailViewModel(
      studentRepository = fakeStudentRepository,
      classRepository = fakeClassRepository,
      paperRepository = fakePaperRepository,
      ocrRepository = FakeOcrRepository(),
      approveEvaluationAction = { evalId, pId ->
        approvalCallCount++
        approvalCalls.add(evalId to pId)
        if (throwCancelOnApprove) {
          throw kotlinx.coroutines.CancellationException("Approval coroutine cancelled")
        }
        val deferred = pendingApprovalDeferred
        if (deferred != null) {
          deferred.await()
        } else {
          Result.success(Unit)
        }
      },
      existingEvaluationDbFetcher = { pId ->
        val deferred = pendingDbLookupDeferred
        if (deferred != null) {
          deferred.await()
        } else {
          com.example.data.model.EvaluationDbRecord(
            id = "eval-db-$pId",
            paperId = pId,
            studentId = "s-safe-1",
            classId = "c-safe-1",
            teacherId = "t-safe-1",
            status = dbEvalStatus,
            totalMarksObtained = 15.0,
            totalMarks = 20.0,
            percentage = 75.0,
            reviewRequired = dbReviewRequired,
            reviewReason = if (dbReviewRequired) "Low confidence" else null,
          ) to listOf(
            com.example.data.model.QuestionDbRecord(
              id = "q-db-1",
              evaluationId = "eval-db-$pId",
              questionNumber = "1",
              questionText = "Q1",
              maximumMarks = 10.0,
              studentAnswer = "A1",
              awardedMarks = 8.0,
              feedback = "Good",
              reviewRequired = dbReviewRequired,
            ),
            com.example.data.model.QuestionDbRecord(
              id = "q-db-2",
              evaluationId = "eval-db-$pId",
              questionNumber = "2",
              questionText = "Q2",
              maximumMarks = 10.0,
              studentAnswer = "A2",
              awardedMarks = 7.0,
              feedback = "Fair",
              reviewRequired = false,
            ),
          )
        }
      },
      saveTeacherCorrectionsAction = { evalId, pId ->
        saveCallCount++
        saveCalls.add(evalId to pId)
        if (throwCancelOnSave) {
          throw kotlinx.coroutines.CancellationException("Save coroutine cancelled")
        }
        val deferred = pendingSaveDeferred
        if (deferred != null) {
          deferred.await()
        } else {
          Result.success(buildEvalResponse(evalId, pId, status = "needs_review", reviewRequired = true, q1Marks = 9.0, q2Marks = 7.0))
        }
      },
    )

    viewModel.loadStudentAndPapers("s-safe-1")
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.papers.size == 2 }
    assertEquals(1, fakeStudentRepository.getStudentByIdCallCount)
    assertEquals(1, fakeClassRepository.getClassByIdCallCount)
    assertEquals(1, fakePaperRepository.getPapersCallCount)
    assertEquals(0, fakePaperRepository.getSignedPageUrlCallCount)

    // 1. Rapid duplicate save calls result in only one save operation
    viewModel.setEvaluationResultForTesting("paper-safe-1", buildEvalResponse("eval-safe-1", "paper-safe-1"))
    viewModel.onUpdateQuestion("eval-safe-1-q1", "1", 9.0, "Updated Q1")
    assertNotNull(viewModel.uiState.value.editedQuestions)

    val saveGate = kotlinx.coroutines.CompletableDeferred<Result<AiEvaluationResponse>>()
    pendingSaveDeferred = saveGate

    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    assertTrue(viewModel.uiState.value.isSavingCorrections)

    waitForCondition { saveCallCount == 1 }
    assertEquals(1, saveCallCount)

    saveGate.complete(
      Result.success(
        buildEvalResponse("eval-safe-1", "paper-safe-1", status = "needs_review", reviewRequired = true, q1Marks = 9.0, q2Marks = 7.0)
      )
    )
    pendingSaveDeferred = null
    waitForCondition { !viewModel.uiState.value.isSavingCorrections }

    assertEquals(1, saveCallCount)
    assertTrue(viewModel.uiState.value.saveCorrectionsSuccess)
    assertNull(viewModel.uiState.value.saveCorrectionsError)
    assertNull(viewModel.uiState.value.editedQuestions)
    assertEquals(9.0, viewModel.uiState.value.evaluationResult?.evaluation?.questions?.first()?.awardedMarks ?: 0.0, 0.001)

    // 2. Rapid duplicate approval calls result in only one approval operation
    // and successful approval updates evaluation & matching paper in memory without redundant reloads
    val studentCallsBeforeApprove = fakeStudentRepository.getStudentByIdCallCount
    val classCallsBeforeApprove = fakeClassRepository.getClassByIdCallCount
    val paperCallsBeforeApprove = fakePaperRepository.getPapersCallCount
    val pageUrlCallsBeforeApprove = fakePaperRepository.getSignedPageUrlCallCount

    val approveGate = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
    pendingApprovalDeferred = approveGate

    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    assertTrue(viewModel.uiState.value.isApproving)

    waitForCondition { approvalCallCount == 1 }
    assertEquals(1, approvalCallCount)

    approveGate.complete(Result.success(Unit))
    pendingApprovalDeferred = null
    waitForCondition { !viewModel.uiState.value.isApproving }

    assertEquals(1, approvalCallCount)
    assertTrue(viewModel.uiState.value.approvalSuccess)
    assertNull(viewModel.uiState.value.approvalError)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.status)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(false, viewModel.uiState.value.evaluationResult?.evaluation?.reviewRequired)
    val approvedPaperAfterGate = viewModel.uiState.value.papers.first { it.id == "paper-safe-1" }
    assertEquals("approved", approvedPaperAfterGate.status)
    assertEquals("approved", approvedPaperAfterGate.evaluation?.status)
    // Verify other papers remain untouched
    assertEquals("needs_review", viewModel.uiState.value.papers.first { it.id == "paper-safe-2" }.status)
    // Verify no redundant student, class, paper, or page reload occurred after approval
    assertEquals(studentCallsBeforeApprove, fakeStudentRepository.getStudentByIdCallCount)
    assertEquals(classCallsBeforeApprove, fakeClassRepository.getClassByIdCallCount)
    assertEquals(paperCallsBeforeApprove, fakePaperRepository.getPapersCallCount)
    assertEquals(pageUrlCallsBeforeApprove, fakePaperRepository.getSignedPageUrlCallCount)

    // 3. Cancellation propagates without being shown as an ordinary failure (for both save and approval)
    // 3a. Cancellation during saveTeacherCorrections
    viewModel.setEvaluationResultForTesting("paper-safe-1", buildEvalResponse("eval-safe-1", "paper-safe-1"))
    viewModel.onUpdateQuestion("eval-safe-1-q1", "1", 9.0, "Edited before cancel")
    saveCallCount = 0
    throwCancelOnSave = true

    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    waitForCondition { !viewModel.uiState.value.isSavingCorrections && saveCallCount == 1 }

    assertEquals(1, saveCallCount)
    assertFalse(viewModel.uiState.value.isSavingCorrections)
    assertFalse(viewModel.uiState.value.saveCorrectionsSuccess)
    assertNull(viewModel.uiState.value.saveCorrectionsError)
    assertNotNull(viewModel.uiState.value.editedQuestions) // Unsaved edits preserved

    // Verify in-flight guard was cleaned up after cancellation so a retry succeeds
    throwCancelOnSave = false
    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    waitForCondition { !viewModel.uiState.value.isSavingCorrections && saveCallCount == 2 }
    assertTrue(viewModel.uiState.value.saveCorrectionsSuccess)
    assertNull(viewModel.uiState.value.saveCorrectionsError)
    assertNull(viewModel.uiState.value.editedQuestions)

    // 3b. Cancellation during approveEvaluation
    approvalCallCount = 0
    throwCancelOnApprove = true
    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    waitForCondition { !viewModel.uiState.value.isApproving && approvalCallCount == 1 }

    assertEquals(1, approvalCallCount)
    assertFalse(viewModel.uiState.value.isApproving)
    assertFalse(viewModel.uiState.value.approvalSuccess)
    assertNull(viewModel.uiState.value.approvalError)
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.evaluation?.status)

    // Verify in-flight guard was cleaned up after cancellation so a retry succeeds without redundant reload
    throwCancelOnApprove = false
    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    waitForCondition { !viewModel.uiState.value.isApproving && approvalCallCount == 2 }
    assertTrue(viewModel.uiState.value.approvalSuccess)
    assertNull(viewModel.uiState.value.approvalError)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals("approved", viewModel.uiState.value.papers.first { it.id == "paper-safe-1" }.status)
    assertEquals("approved", viewModel.uiState.value.papers.first { it.id == "paper-safe-1" }.evaluation?.status)
    assertEquals(studentCallsBeforeApprove, fakeStudentRepository.getStudentByIdCallCount)
    assertEquals(classCallsBeforeApprove, fakeClassRepository.getClassByIdCallCount)
    assertEquals(paperCallsBeforeApprove, fakePaperRepository.getPapersCallCount)
    assertEquals(pageUrlCallsBeforeApprove, fakePaperRepository.getSignedPageUrlCallCount)

    // 4. openApprovedEvaluation() preserves actual database status and reviewRequired flag:
    // never presents 'needs_review' or 'completed' as 'approved'
    // 4a. DB status is 'needs_review' with reviewRequired = true
    viewModel.dismissEvaluationDialog()
    dbEvalStatus = "needs_review"
    dbReviewRequired = true
    viewModel.openApprovedEvaluation("paper-safe-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.status)
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(true, viewModel.uiState.value.evaluationResult?.evaluation?.reviewRequired)
    assertEquals(
      "needs_review",
      viewModel.uiState.value.papers.first { it.id == "paper-safe-1" }.status,
    )

    // 4b. DB status is 'completed' with reviewRequired = false
    viewModel.dismissEvaluationDialog()
    dbEvalStatus = "completed"
    dbReviewRequired = false
    viewModel.openApprovedEvaluation("paper-safe-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals("completed", viewModel.uiState.value.evaluationResult?.status)
    assertEquals("completed", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(false, viewModel.uiState.value.evaluationResult?.evaluation?.reviewRequired)
    assertEquals(
      "completed",
      viewModel.uiState.value.papers.first { it.id == "paper-safe-1" }.status,
    )

    // 4c. DB status is genuinely 'approved'
    viewModel.dismissEvaluationDialog()
    dbEvalStatus = "approved"
    dbReviewRequired = false
    viewModel.openApprovedEvaluation("paper-safe-1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null && viewModel.uiState.value.evaluationResult != null }

    assertEquals("approved", viewModel.uiState.value.evaluationResult?.status)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(false, viewModel.uiState.value.evaluationResult?.evaluation?.reviewRequired)

    // 5. A stale suspended operation finishing after the selected paper changes cannot overwrite the new paper's state
    // 5a. Stale approveEvaluation for paper-safe-1 finishes after switching to paper-safe-2
    viewModel.setEvaluationResultForTesting("paper-safe-1", buildEvalResponse("eval-safe-1", "paper-safe-1"))
    val staleApproveGate = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
    pendingApprovalDeferred = staleApproveGate
    approvalCallCount = 0

    viewModel.approveEvaluation("eval-safe-1", "paper-safe-1")
    waitForCondition { approvalCallCount == 1 }

    // Switch active paper to paper-safe-2 while paper-safe-1 approval is still suspended
    viewModel.setEvaluationResultForTesting("paper-safe-2", buildEvalResponse("eval-safe-2", "paper-safe-2", q1Marks = 6.0, q2Marks = 6.0))
    assertEquals("paper-safe-2", viewModel.uiState.value.evaluatedPaperId)
    assertFalse(viewModel.uiState.value.isApproving)

    // Complete stale paper-safe-1 approval
    staleApproveGate.complete(Result.success(Unit))
    pendingApprovalDeferred = null
    Thread.sleep(50)
    mainLooper.idle()

    assertEquals("paper-safe-2", viewModel.uiState.value.evaluatedPaperId)
    assertEquals("eval-safe-2", viewModel.uiState.value.evaluationResult?.evaluation?.id)
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertFalse(viewModel.uiState.value.approvalSuccess)

    // 5b. Stale saveTeacherCorrections for paper-safe-1 finishes after switching to paper-safe-2
    viewModel.setEvaluationResultForTesting("paper-safe-1", buildEvalResponse("eval-safe-1", "paper-safe-1"))
    viewModel.onUpdateQuestion("eval-safe-1-q1", "1", 9.0, "Paper 1 edit")
    val staleSaveGate = kotlinx.coroutines.CompletableDeferred<Result<AiEvaluationResponse>>()
    pendingSaveDeferred = staleSaveGate
    saveCallCount = 0

    viewModel.saveTeacherCorrections("eval-safe-1", "paper-safe-1")
    waitForCondition { saveCallCount == 1 }

    // Switch active paper to paper-safe-2 and make an unsaved edit on paper-safe-2
    viewModel.setEvaluationResultForTesting("paper-safe-2", buildEvalResponse("eval-safe-2", "paper-safe-2", q1Marks = 6.0, q2Marks = 6.0))
    viewModel.onUpdateQuestion("eval-safe-2-q1", "1", 10.0, "Paper 2 unsaved edit")
    assertEquals("paper-safe-2", viewModel.uiState.value.evaluatedPaperId)
    assertNotNull(viewModel.uiState.value.editedQuestions)

    // Complete stale paper-safe-1 save
    staleSaveGate.complete(
      Result.success(buildEvalResponse("eval-safe-1", "paper-safe-1", q1Marks = 9.0, q2Marks = 7.0))
    )
    pendingSaveDeferred = null
    Thread.sleep(50)
    mainLooper.idle()

    assertEquals("paper-safe-2", viewModel.uiState.value.evaluatedPaperId)
    assertEquals("eval-safe-2", viewModel.uiState.value.evaluationResult?.evaluation?.id)
    assertNotNull(viewModel.uiState.value.editedQuestions)
    assertEquals(10.0, viewModel.uiState.value.editedQuestions?.first()?.awardedMarks ?: 0.0, 0.001)
    assertFalse(viewModel.uiState.value.saveCorrectionsSuccess)

    // 5c. Stale openApprovedEvaluation for paper-safe-1 finishes after switching to paper-safe-2
    val staleLookupGate = kotlinx.coroutines.CompletableDeferred<Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>>?>()
    pendingDbLookupDeferred = staleLookupGate
    viewModel.openApprovedEvaluation("paper-safe-1")
    assertEquals("paper-safe-1", viewModel.uiState.value.isEvaluatingPaperId)

    // Switch active paper to paper-safe-2 while paper-safe-1 lookup is suspended
    viewModel.setEvaluationResultForTesting("paper-safe-2", buildEvalResponse("eval-safe-2", "paper-safe-2", q1Marks = 6.0, q2Marks = 6.0))
    staleLookupGate.complete(null)
    pendingDbLookupDeferred = null
    Thread.sleep(50)
    mainLooper.idle()

    assertEquals("paper-safe-2", viewModel.uiState.value.evaluatedPaperId)
    assertEquals("eval-safe-2", viewModel.uiState.value.evaluationResult?.evaluation?.id)
    assertNull(viewModel.uiState.value.evaluationError)
  }

  @Test
  fun studentResultDetailViewModel_parallelDirectLookupNumericalSortStaleAndErrorHandling() {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

    val student1 = Student(
      id = "stu-res-1",
      classId = "cls-res-1",
      teacherId = "user-123",
      name = "Amina Tariq",
      fatherName = "Tariq Mahmood",
      rollNumber = "101",
    )
    val student2 = Student(
      id = "stu-res-2",
      classId = "cls-res-1",
      teacherId = "user-123",
      name = "Bilal Ahmed",
      fatherName = "Ahmed Raza",
      rollNumber = "102",
    )
    val classRoom = ClassRoom(
      id = "cls-res-1",
      examId = "exam-res-1",
      teacherId = "user-123",
      session = "2025-2026",
      semester = "Fall",
      timing = "Morning",
      subjectName = "Physics",
      totalMarks = 50,
    )

    val studentGate = kotlinx.coroutines.CompletableDeferred<Result<Student?>>()
    val classGate = kotlinx.coroutines.CompletableDeferred<Result<ClassRoom?>>()
    val evalGate = kotlinx.coroutines.CompletableDeferred<Pair<EvaluationDbRecord, List<QuestionDbRecord>>?>()

    val studentRepo = FakeStudentRepository(
      initialStudents = listOf(student1, student2),
      deferredStudentByIdResult = studentGate,
    )
    val classRepo = FakeClassRepository(
      initialClasses = listOf(classRoom),
      deferredClassByIdResult = classGate,
    )

    var directEvalLookupCount = 0
    var activeEvalDeferred: kotlinx.coroutines.CompletableDeferred<Pair<EvaluationDbRecord, List<QuestionDbRecord>>?>? = evalGate
    var shouldThrowEvalError = false
    var shouldThrowEvalCancellation = false
    var staticEvalResult: Pair<EvaluationDbRecord, List<QuestionDbRecord>>? = null

    val viewModel = StudentResultDetailViewModel(
      studentRepository = studentRepo,
      classRepository = classRepo,
      approvedEvaluationFetcher = { reqStudentId, reqClassId ->
        directEvalLookupCount++
        if (shouldThrowEvalCancellation) {
          throw kotlinx.coroutines.CancellationException("Simulated evaluation query cancellation")
        }
        if (shouldThrowEvalError) {
          throw IllegalStateException("Supabase connection timeout while loading evaluation")
        }
        val gate = activeEvalDeferred
        if (gate != null) {
          gate.await()
        } else {
          staticEvalResult?.takeIf {
            it.first.studentId == reqStudentId && it.first.classId == reqClassId
          }
        }
      },
    )

    fun waitForCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (predicate()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    // 1. Parallel execution & single direct approved evaluation lookup (no N+1 paper loop)
    viewModel.loadStudentResult("stu-res-1", "cls-res-1")
    waitForCondition {
      studentRepo.getStudentByIdCallCount == 1 &&
        classRepo.getClassByIdCallCount == 1 &&
        directEvalLookupCount == 1
    }
    // All 3 independent lookups started concurrently before any gate completed
    assertEquals(1, studentRepo.getStudentByIdCallCount)
    assertEquals(1, classRepo.getClassByIdCallCount)
    assertEquals(1, directEvalLookupCount)
    assertTrue(viewModel.uiState.value.isLoading)

    val evalRecord1 = EvaluationDbRecord(
      id = "eval-res-1",
      paperId = "paper-res-1",
      studentId = "stu-res-1",
      classId = "cls-res-1",
      teacherId = "user-123",
      status = "approved",
      totalMarksObtained = 39.5,
      totalMarks = 50.0,
      percentage = 79.0,
    )
    // Unordered questions to verify numerical sorting
    val unsortedQuestions = listOf(
      QuestionDbRecord(
        id = "q-10",
        evaluationId = "eval-res-1",
        questionNumber = "10",
        questionText = "Question Ten",
        studentAnswer = "Ans 10",
        awardedMarks = 8.0,
        maximumMarks = 10.0,
        feedback = "Good",
      ),
      QuestionDbRecord(
        id = "q-2b",
        evaluationId = "eval-res-1",
        questionNumber = "2(b)",
        questionText = "Question Two B",
        studentAnswer = "Ans 2b",
        awardedMarks = 4.5,
        maximumMarks = 5.0,
        feedback = "Nice",
      ),
      QuestionDbRecord(
        id = "q-1",
        evaluationId = "eval-res-1",
        questionNumber = "1",
        questionText = "Question One",
        studentAnswer = "Ans 1",
        awardedMarks = 10.0,
        maximumMarks = 10.0,
        feedback = "Excellent",
      ),
      QuestionDbRecord(
        id = "q-2a",
        evaluationId = "eval-res-1",
        questionNumber = "2(a)",
        questionText = "Question Two A",
        studentAnswer = "Ans 2a",
        awardedMarks = 5.0,
        maximumMarks = 5.0,
        feedback = "Complete",
      ),
      QuestionDbRecord(
        id = "q-2",
        evaluationId = "eval-res-1",
        questionNumber = "2",
        questionText = "Question Two Main",
        studentAnswer = "Ans 2",
        awardedMarks = 12.0,
        maximumMarks = 15.0,
        feedback = "Well done",
      ),
    )

    studentGate.complete(Result.success(student1))
    classGate.complete(Result.success(classRoom))
    evalGate.complete(Pair(evalRecord1, unsortedQuestions))
    studentRepo.deferredStudentByIdResult = null
    classRepo.deferredClassByIdResult = null
    activeEvalDeferred = null

    waitForCondition { !viewModel.uiState.value.isLoading }

    // 2. Numerical question ordering & preserved result calculations
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals("Amina Tariq", viewModel.uiState.value.student?.name)
    assertEquals("Physics", viewModel.uiState.value.classRoom?.subjectName)
    assertEquals("eval-res-1", viewModel.uiState.value.evaluation?.id)
    assertEquals(39.5, viewModel.uiState.value.evaluation?.totalMarksObtained ?: 0.0, 0.001)
    assertEquals(50.0, viewModel.uiState.value.evaluation?.totalMarks ?: 0.0, 0.001)
    assertEquals(79.0, viewModel.uiState.value.evaluation?.percentage ?: 0.0, 0.001)
    assertEquals(
      listOf("1", "2", "2(a)", "2(b)", "10"),
      viewModel.uiState.value.evaluation?.questions?.map { it.questionNumber },
    )
    assertEquals(45.0, viewModel.uiState.value.evaluation?.extractedQuestionTotalMarks ?: 0.0, 0.001)

    // 3. Database error on refresh surfaces errorMessage and preserves prior valid result
    shouldThrowEvalError = true
    viewModel.loadStudentResult("stu-res-1", "cls-res-1")
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.errorMessage != null }
    assertTrue(viewModel.uiState.value.errorMessage!!.contains("Supabase connection timeout"))
    // Previous valid evaluation is still preserved rather than falsely wiped to "No Approved Result Yet"
    assertEquals("eval-res-1", viewModel.uiState.value.evaluation?.id)
    shouldThrowEvalError = false

    // 4. Approved-only and student/class ownership filtering
    val unapprovedRecord = evalRecord1.copy(id = "eval-unapproved", status = "needs_review")
    staticEvalResult = Pair(unapprovedRecord, unsortedQuestions)
    viewModel.loadStudentResult("stu-res-2", "cls-res-1")
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.student?.id == "stu-res-2" }
    assertNull(viewModel.uiState.value.errorMessage)
    assertNull(viewModel.uiState.value.evaluation)

    // 5. Cancellation handling (both direct CancellationException and wrapped in Result.failure)
    shouldThrowEvalCancellation = true
    viewModel.loadStudentResult("stu-res-2", "cls-res-1")
    Thread.sleep(40)
    mainLooper.idle()
    assertNull(viewModel.uiState.value.errorMessage)
    shouldThrowEvalCancellation = false

    studentRepo.throwCancellation = true
    studentRepo.swallowCancellationIntoFailure = true
    viewModel.loadStudentResult("stu-res-2", "cls-res-1")
    Thread.sleep(40)
    mainLooper.idle()
    assertNull(viewModel.uiState.value.errorMessage)
    studentRepo.throwCancellation = false
    studentRepo.swallowCancellationIntoFailure = false

    // 6. Stale-request protection when switching from stu-res-1 to stu-res-2
    val staleEvalGate = kotlinx.coroutines.CompletableDeferred<Pair<EvaluationDbRecord, List<QuestionDbRecord>>?>()
    activeEvalDeferred = staleEvalGate
    viewModel.loadStudentResult("stu-res-1", "cls-res-1")
    waitForCondition { viewModel.uiState.value.isLoading }

    // Switch immediately to stu-res-2 with a fast response
    activeEvalDeferred = null
    val evalRecord2 = evalRecord1.copy(
      id = "eval-res-2",
      paperId = "paper-res-2",
      studentId = "stu-res-2",
      totalMarksObtained = 48.0,
      percentage = 96.0,
    )
    staticEvalResult = Pair(evalRecord2, listOf(unsortedQuestions[2].copy(evaluationId = "eval-res-2")))
    viewModel.loadStudentResult("stu-res-2", "cls-res-1")
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.evaluation?.id == "eval-res-2" }

    // Complete stale stu-res-1 gate afterwards; state must remain stu-res-2
    staleEvalGate.complete(Pair(evalRecord1, unsortedQuestions))
    Thread.sleep(40)
    mainLooper.idle()
    assertEquals("stu-res-2", viewModel.uiState.value.student?.id)
    assertEquals("eval-res-2", viewModel.uiState.value.evaluation?.id)
  }

  @Test
  fun classResultsAndReportViewModels_errorReportingStaleCancellationAndOwnershipFiltering() {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

    val exam = Exam(
      id = "exam-cr-1",
      teacherId = "user-123",
      name = "Final Term 2026",
      type = "Final",
      date = "2026-10-15",
    )
    val class1 = ClassRoom(
      id = "cls-cr-1",
      examId = "exam-cr-1",
      teacherId = "user-123",
      session = "2025-2026",
      semester = "Spring",
      timing = "Morning",
      subjectName = "Chemistry",
      totalMarks = 100,
    )
    val class2 = ClassRoom(
      id = "cls-cr-2",
      examId = "exam-cr-1",
      teacherId = "user-123",
      session = "2025-2026",
      semester = "Spring",
      timing = "Evening",
      subjectName = "Mathematics",
      totalMarks = 100,
    )
    val stu1 = Student(
      id = "stu-cr-1",
      classId = "cls-cr-1",
      teacherId = "user-123",
      name = "Zainab Ali",
      fatherName = "Ali Hassan",
      rollNumber = "01",
    )
    val stu2 = Student(
      id = "stu-cr-2",
      classId = "cls-cr-1",
      teacherId = "user-123",
      name = "Omar Farooq",
      fatherName = "Farooq Azam",
      rollNumber = "02",
    )
    val stuClass2 = Student(
      id = "stu-cr-3",
      classId = "cls-cr-2",
      teacherId = "user-123",
      name = "Hina Khan",
      fatherName = "Imran Khan",
      rollNumber = "01",
    )

    val examRepo = FakeExamRepository(initialExams = listOf(exam))
    val classRepo = FakeClassRepository(initialClasses = listOf(class1, class2))
    val studentRepo = FakeStudentRepository(initialStudents = listOf(stu1, stu2, stuClass2))

    var shouldFailEvalQuery = false
    var shouldCancelEvalQuery = false
    var evalQueryGate: kotlinx.coroutines.CompletableDeferred<List<EvaluationDbRecord>>? = null
    var staticEvaluationsByClass: Map<String, List<EvaluationDbRecord>> = emptyMap()

    val resultsViewModel = ClassResultsViewModel(
      classRepository = classRepo,
      studentRepository = studentRepo,
      approvedEvaluationsFetcher = { reqClassId ->
        if (shouldCancelEvalQuery) {
          throw kotlinx.coroutines.CancellationException("Simulated evaluations query cancellation")
        }
        if (shouldFailEvalQuery) {
          throw IllegalStateException("Database unavailable while fetching evaluations")
        }
        val gate = evalQueryGate
        if (gate != null) {
          gate.await()
        } else {
          staticEvaluationsByClass[reqClassId].orEmpty()
        }
      },
    )

    val reportViewModel = ClassReportViewModel(
      classRepository = classRepo,
      examRepository = examRepo,
      studentRepository = studentRepo,
      approvedEvaluationsFetcher = { reqClassId ->
        if (shouldCancelEvalQuery) {
          throw kotlinx.coroutines.CancellationException("Simulated evaluations query cancellation")
        }
        if (shouldFailEvalQuery) {
          throw IllegalStateException("Database unavailable while fetching evaluations")
        }
        val gate = evalQueryGate
        if (gate != null) {
          gate.await()
        } else {
          staticEvaluationsByClass[reqClassId].orEmpty()
        }
      },
    )

    fun waitForCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (predicate()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    // 1. Database error on initial load must set errorMessage and NOT populate students as "Not Evaluated"
    shouldFailEvalQuery = true
    resultsViewModel.loadClassResults("cls-cr-1")
    reportViewModel.loadClassReport("cls-cr-1")
    waitForCondition {
      !resultsViewModel.uiState.value.isLoading && !reportViewModel.uiState.value.isLoading
    }

    assertNotNull(resultsViewModel.uiState.value.errorMessage)
    assertTrue(resultsViewModel.uiState.value.students.isEmpty())
    assertTrue(resultsViewModel.uiState.value.evaluationsByStudentId.isEmpty())

    assertNotNull(reportViewModel.uiState.value.errorMessage)
    assertTrue(reportViewModel.uiState.value.students.isEmpty())
    assertTrue(reportViewModel.uiState.value.evaluationsByStudentId.isEmpty())
    shouldFailEvalQuery = false

    // 2. Approved-only and student/class ownership filtering
    val approvedStu1Latest = EvaluationDbRecord(
      id = "eval-stu1-latest",
      paperId = "p-1",
      studentId = "stu-cr-1",
      classId = "cls-cr-1",
      teacherId = "user-123",
      status = "approved",
      totalMarksObtained = 88.0,
      totalMarks = 100.0,
      percentage = 88.0,
    )
    val approvedStu1Older = EvaluationDbRecord(
      id = "eval-stu1-older",
      paperId = "p-0",
      studentId = "stu-cr-1",
      classId = "cls-cr-1",
      teacherId = "user-123",
      status = "approved",
      totalMarksObtained = 70.0,
      totalMarks = 100.0,
      percentage = 70.0,
    )
    val unapprovedStu2 = EvaluationDbRecord(
      id = "eval-stu2-draft",
      paperId = "p-2",
      studentId = "stu-cr-2",
      classId = "cls-cr-1",
      teacherId = "user-123",
      status = "needs_review",
      totalMarksObtained = 95.0,
      totalMarks = 100.0,
      percentage = 95.0,
    )
    val foreignStudentEval = EvaluationDbRecord(
      id = "eval-foreign",
      paperId = "p-3",
      studentId = "stu-cr-3",
      classId = "cls-cr-1",
      teacherId = "user-123",
      status = "approved",
      totalMarksObtained = 91.0,
      totalMarks = 100.0,
      percentage = 91.0,
    )
    staticEvaluationsByClass = mapOf(
      "cls-cr-1" to listOf(approvedStu1Older, approvedStu1Latest, unapprovedStu2, foreignStudentEval),
      "cls-cr-2" to listOf(
        EvaluationDbRecord(
          id = "eval-cls2-stu3",
          paperId = "p-4",
          studentId = "stu-cr-3",
          classId = "cls-cr-2",
          teacherId = "user-123",
          status = "approved",
          totalMarksObtained = 92.0,
          totalMarks = 100.0,
          percentage = 92.0,
        )
      ),
    )

    resultsViewModel.loadClassResults("cls-cr-1")
    reportViewModel.loadClassReport("cls-cr-1")
    waitForCondition {
      !resultsViewModel.uiState.value.isLoading && !reportViewModel.uiState.value.isLoading
    }

    assertNull(resultsViewModel.uiState.value.errorMessage)
    assertEquals(2, resultsViewModel.uiState.value.students.size)
    assertEquals(1, resultsViewModel.uiState.value.evaluationsByStudentId.size)
    assertEquals(88.0, resultsViewModel.uiState.value.evaluationsByStudentId["stu-cr-1"]?.totalMarksObtained ?: 0.0, 0.001)
    assertNull(resultsViewModel.uiState.value.evaluationsByStudentId["stu-cr-2"])
    assertNull(resultsViewModel.uiState.value.evaluationsByStudentId["stu-cr-3"])

    assertNull(reportViewModel.uiState.value.errorMessage)
    assertEquals("Final Term 2026", reportViewModel.uiState.value.exam?.name)
    assertEquals(2, reportViewModel.uiState.value.students.size)
    assertEquals(1, reportViewModel.uiState.value.evaluationsByStudentId.size)
    assertEquals(88.0, reportViewModel.uiState.value.evaluationsByStudentId["stu-cr-1"]?.totalMarksObtained ?: 0.0, 0.001)

    // 3. Failed refresh preserves existing valid state and sets errorMessage
    shouldFailEvalQuery = true
    resultsViewModel.loadClassResults("cls-cr-1")
    reportViewModel.loadClassReport("cls-cr-1")
    waitForCondition {
      !resultsViewModel.uiState.value.isLoading && !reportViewModel.uiState.value.isLoading
    }
    assertNotNull(resultsViewModel.uiState.value.errorMessage)
    assertEquals(1, resultsViewModel.uiState.value.evaluationsByStudentId.size)
    assertNotNull(reportViewModel.uiState.value.errorMessage)
    assertEquals(1, reportViewModel.uiState.value.evaluationsByStudentId.size)
    shouldFailEvalQuery = false

    // 4. Cancellation handling does not set errorMessage
    shouldCancelEvalQuery = true
    resultsViewModel.loadClassResults("cls-cr-1")
    reportViewModel.loadClassReport("cls-cr-1")
    Thread.sleep(40)
    mainLooper.idle()
    assertNull(resultsViewModel.uiState.value.errorMessage)
    assertNull(reportViewModel.uiState.value.errorMessage)
    shouldCancelEvalQuery = false

    // 5. Stale-request protection when switching from cls-cr-1 to cls-cr-2
    val staleGate = kotlinx.coroutines.CompletableDeferred<List<EvaluationDbRecord>>()
    evalQueryGate = staleGate
    resultsViewModel.loadClassResults("cls-cr-1")
    reportViewModel.loadClassReport("cls-cr-1")
    waitForCondition { resultsViewModel.uiState.value.isLoading && reportViewModel.uiState.value.isLoading }

    evalQueryGate = null
    resultsViewModel.loadClassResults("cls-cr-2")
    reportViewModel.loadClassReport("cls-cr-2")
    waitForCondition {
      !resultsViewModel.uiState.value.isLoading &&
        !reportViewModel.uiState.value.isLoading &&
        resultsViewModel.uiState.value.classRoom?.id == "cls-cr-2" &&
        reportViewModel.uiState.value.classRoom?.id == "cls-cr-2"
    }

    staleGate.complete(listOf(approvedStu1Latest))
    Thread.sleep(40)
    mainLooper.idle()
    assertEquals("cls-cr-2", resultsViewModel.uiState.value.classRoom?.id)
    assertEquals("eval-cls2-stu3", resultsViewModel.uiState.value.evaluationsByStudentId["stu-cr-3"]?.id)
    assertEquals("cls-cr-2", reportViewModel.uiState.value.classRoom?.id)
    assertEquals("eval-cls2-stu3", reportViewModel.uiState.value.evaluationsByStudentId["stu-cr-3"]?.id)
  }

  @Test
  fun evaluationAndOcrHttpCancellation_cancelsUnderlyingOkHttpCallsWithoutFalseErrorsOrCrossInterference() {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

    fun waitForCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (predicate()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    val student = Student(
      id = "stu-http-1",
      classId = "cls-http-1",
      teacherId = "user-123",
      name = "Sana Tariq",
      fatherName = "Tariq Aziz",
      rollNumber = "15",
    )
    val classRoom = ClassRoom(
      id = "cls-http-1",
      examId = "exam-http-1",
      teacherId = "user-123",
      session = "2025-2026",
      semester = "Fall",
      timing = "Morning",
      subjectName = "Computer Networks",
      totalMarks = 20,
    )
    val paper1 = StudentPaper(
      id = "paper-http-1",
      studentId = "stu-http-1",
      classId = "cls-http-1",
      teacherId = "user-123",
      totalPages = 1,
      pages = listOf(
        StudentPaperPage(
          id = "page-http-1",
          paperId = "paper-http-1",
          studentId = "stu-http-1",
          teacherId = "user-123",
          pageNumber = 1,
          storagePath = "user-123/paper-http-1/page_1.jpg",
        )
      ),
    )
    val paper2 = StudentPaper(
      id = "paper-http-2",
      studentId = "stu-http-1",
      classId = "cls-http-1",
      teacherId = "user-123",
      totalPages = 1,
      pages = listOf(
        StudentPaperPage(
          id = "page-http-2",
          paperId = "paper-http-2",
          studentId = "stu-http-1",
          teacherId = "user-123",
          pageNumber = 1,
          storagePath = "user-123/paper-http-2/page_1.jpg",
        )
      ),
    )

    // --- Part A: Evaluation HTTP Call Cancellation & Lifecycle ---
    val recordedEvalCalls = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>()
    val canceledEvalCalls = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>()
    var holdEvalHttpCall = false
    var evalHttpStatusToReturn = 200
    var evalHttpThrowNetworkError = false

    val evalHttpClient = okhttp3.OkHttpClient.Builder()
      .eventListener(object : okhttp3.EventListener() {
        override fun canceled(call: okhttp3.Call) {
          canceledEvalCalls.add(call)
        }
      })
      .addInterceptor { chain ->
        val call = chain.call()
        recordedEvalCalls.add(call)
        if (holdEvalHttpCall) {
          val deadline = System.currentTimeMillis() + 3000L
          while (!call.isCanceled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
          }
          if (call.isCanceled()) {
            throw java.io.IOException("Canceled")
          }
        }
        if (evalHttpThrowNetworkError) {
          throw java.io.IOException("Connection reset by peer")
        }
        val bodyJson = if (evalHttpStatusToReturn == 200) {
          """
            {
              "status": "completed",
              "percentageAvailable": true,
              "evaluation": {
                "id": "eval-http-ok",
                "paperId": "paper-http-2",
                "studentId": "stu-http-1",
                "classId": "cls-http-1",
                "teacherId": "user-123",
                "status": "completed",
                "totalMarksObtained": 19.0,
                "totalMarks": 20.0,
                "percentage": 95.0,
                "reviewRequired": false,
                "questions": [
                  {
                    "id": "q-http-1",
                    "evaluation_id": "eval-http-ok",
                    "question_number": "1",
                    "question_text": "What is TCP?",
                    "maximum_marks": 20.0,
                    "student_answer": "Transmission Control Protocol",
                    "awarded_marks": 19.0,
                    "feedback": "Accurate"
                  }
                ]
              }
            }
          """.trimIndent()
        } else {
          """{"error":"Edge Function internal error"}"""
        }
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(evalHttpStatusToReturn)
          .message(if (evalHttpStatusToReturn == 200) "OK" else "Error")
          .body(with(okhttp3.ResponseBody) { bodyJson.toResponseBody(null) })
          .build()
      }
      .build()

    // --- Part B: OCR HTTP Call Cancellation & Lifecycle ---
    val recordedOcrCalls = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>()
    val canceledOcrCalls = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Call>()
    var holdOcrHttpCall = false
    var ocrHttpStatusToReturn = 200
    var ocrResponseText = "Transcribed page text from OCR Edge Function."

    val ocrHttpClient = okhttp3.OkHttpClient.Builder()
      .eventListener(object : okhttp3.EventListener() {
        override fun canceled(call: okhttp3.Call) {
          canceledOcrCalls.add(call)
        }
      })
      .addInterceptor { chain ->
        val call = chain.call()
        recordedOcrCalls.add(call)
        if (holdOcrHttpCall) {
          val deadline = System.currentTimeMillis() + 3000L
          while (!call.isCanceled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
          }
          if (call.isCanceled()) {
            throw java.io.IOException("Canceled")
          }
        }
        val bodyJson = if (ocrHttpStatusToReturn == 200) {
          """{"status":"completed","ocrText":"$ocrResponseText"}"""
        } else {
          """{"status":"failed","error":"OCR service overloaded"}"""
        }
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(ocrHttpStatusToReturn)
          .message(if (ocrHttpStatusToReturn == 200) "OK" else "Error")
          .body(with(okhttp3.ResponseBody) { bodyJson.toResponseBody(null) })
          .build()
      }
      .build()

    val realOcrRepository = SupabaseOcrRepository(
      authRepository = FakeAuthRepository(signedInUser = "teacher@example.com"),
      httpClient = ocrHttpClient,
      accessTokenProvider = { "test-ocr-jwt-token" },
    )

    var lookupCount = 0
    val studentRepo = FakeStudentRepository(listOf(student))
    val classRepo = FakeClassRepository(listOf(classRoom))
    val paperRepo = FakePaperRepository(listOf(paper1, paper2))

    val viewModel = StudentDetailViewModel(
      studentRepository = studentRepo,
      classRepository = classRepo,
      paperRepository = paperRepo,
      ocrRepository = realOcrRepository,
      evaluationHttpClient = evalHttpClient,
      existingEvaluationDbFetcher = {
        lookupCount++
        null
      },
      evaluationAccessTokenProvider = { "test-eval-jwt-token" },
    )

    viewModel.loadStudentAndPapers("stu-http-1")
    waitForCondition { !viewModel.uiState.value.isLoading }

    // 1. Cancelling an in-flight evaluation via stopAiEvaluation() cancels its underlying OkHttp Call,
    //    does NOT set evaluationError, and does NOT trigger extra fallback lookups
    holdEvalHttpCall = true
    lookupCount = 0
    viewModel.runAiEvaluation("paper-http-1")
    waitForCondition { recordedEvalCalls.size == 1 }
    val evalCall1 = recordedEvalCalls[0]
    assertFalse(evalCall1.isCanceled())

    viewModel.stopAiEvaluation("paper-http-1")
    waitForCondition { evalCall1.isCanceled() && canceledEvalCalls.contains(evalCall1) }
    assertTrue(evalCall1.isCanceled())
    assertTrue(canceledEvalCalls.contains(evalCall1))
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertNull(viewModel.uiState.value.evaluationError)
    // Only the single initial lookup ran; no unintended retry or 409 fallback lookup occurred
    assertEquals(1, lookupCount)

    // 2. Starting a newer evaluation after cancelling an older one does not cancel the newer Call
    holdEvalHttpCall = false
    evalHttpStatusToReturn = 200
    viewModel.runAiEvaluation("paper-http-2")
    waitForCondition {
      recordedEvalCalls.size == 2 &&
        viewModel.uiState.value.isEvaluatingPaperId == null &&
        viewModel.uiState.value.evaluationResult != null
    }
    val evalCall2 = recordedEvalCalls[1]
    assertTrue(evalCall1.isCanceled())
    assertFalse(evalCall2.isCanceled())
    assertNull(viewModel.uiState.value.evaluationError)
    assertEquals("eval-http-ok", viewModel.uiState.value.evaluationResult?.evaluation?.id)

    // 3. Genuine HTTP failure on evaluation still reports evaluationError properly
    viewModel.setEvaluationResultForTesting("paper-http-1", null)
    evalHttpStatusToReturn = 500
    viewModel.runAiEvaluation("paper-http-1")
    waitForCondition {
      recordedEvalCalls.size == 3 &&
        viewModel.uiState.value.isEvaluatingPaperId == null &&
        viewModel.uiState.value.evaluationError != null
    }
    assertFalse(recordedEvalCalls[2].isCanceled())
    assertTrue(viewModel.uiState.value.evaluationError!!.contains("HTTP 500"))
    evalHttpStatusToReturn = 200

    // 4. Direct coroutine cancellation on SupabaseOcrRepository.processPageOcr() cancels OkHttp Call
    //    and propagates CancellationException (never returning Result.failure)
    holdOcrHttpCall = true
    var ocrCoroutineOutcome: Throwable? = null
    val ocrTestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val directOcrJob = ocrTestScope.launch {
      try {
        realOcrRepository.processPageOcr("paper-http-1", "page-http-1")
      } catch (t: Throwable) {
        ocrCoroutineOutcome = t
        throw t
      }
    }
    waitForCondition { recordedOcrCalls.size == 1 }
    val ocrCall1 = recordedOcrCalls[0]
    assertFalse(ocrCall1.isCanceled())

    directOcrJob.cancel()
    waitForCondition { ocrCall1.isCanceled() && ocrCoroutineOutcome != null }
    assertTrue(ocrCall1.isCanceled())
    assertTrue(canceledOcrCalls.contains(ocrCall1))
    assertTrue(ocrCoroutineOutcome is kotlinx.coroutines.CancellationException)

    // 5. Cancelling an in-flight OCR request in StudentDetailViewModel cancels its OkHttp Call
    //    without setting ocrErrorByPageId, and does not interfere with a subsequent OCR request
    viewModel.runOcrForPage("paper-http-1", "page-http-1")
    waitForCondition { recordedOcrCalls.size == 2 }
    val ocrCall2 = recordedOcrCalls[1]
    assertTrue("page-http-1" in viewModel.uiState.value.ocrLoadingPageIds)
    assertFalse(ocrCall2.isCanceled())

    viewModel.cancelOcrForPage("page-http-1")
    waitForCondition { ocrCall2.isCanceled() && "page-http-1" !in viewModel.uiState.value.ocrLoadingPageIds }
    assertTrue(ocrCall2.isCanceled())
    assertTrue(canceledOcrCalls.contains(ocrCall2))
    assertNull(viewModel.uiState.value.ocrErrorByPageId["page-http-1"])
    assertNull(viewModel.uiState.value.ocrTextByPageId["page-http-1"])

    // Newer OCR request succeeds normally and its Call is not cancelled by the earlier cancelled request
    holdOcrHttpCall = false
    ocrHttpStatusToReturn = 200
    ocrResponseText = "Normalized 3NF schema explanation."
    viewModel.runOcrForPage("paper-http-1", "page-http-1")
    waitForCondition {
      recordedOcrCalls.size == 3 &&
        "page-http-1" !in viewModel.uiState.value.ocrLoadingPageIds &&
        viewModel.uiState.value.ocrTextByPageId["page-http-1"] != null
    }
    val ocrCall3 = recordedOcrCalls[2]
    assertFalse(ocrCall3.isCanceled())
    assertEquals("Normalized 3NF schema explanation.", viewModel.uiState.value.ocrTextByPageId["page-http-1"])
    assertNull(viewModel.uiState.value.ocrErrorByPageId["page-http-1"])

    // Genuine OCR HTTP error still surfaces in ocrErrorByPageId
    ocrHttpStatusToReturn = 503
    viewModel.runOcrForPage("paper-http-2", "page-http-2")
    waitForCondition {
      recordedOcrCalls.size == 4 &&
        "page-http-2" !in viewModel.uiState.value.ocrLoadingPageIds &&
        viewModel.uiState.value.ocrErrorByPageId["page-http-2"] != null
    }
    assertFalse(recordedOcrCalls[3].isCanceled())
    assertEquals("OCR service overloaded", viewModel.uiState.value.ocrErrorByPageId["page-http-2"])
  }

  @Test
  fun teacherReviewInitialLoadRace_waitsForPaperLoadReusesApprovedAndPreventsStaleEvaluation() {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())

    fun waitForCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() < deadline) {
        mainLooper.idle()
        if (predicate()) return
        Thread.sleep(10)
      }
      mainLooper.idle()
    }

    var student1PapersDeferred = kotlinx.coroutines.CompletableDeferred<Result<List<StudentPaper>>>()
    var student2PapersDeferred = kotlinx.coroutines.CompletableDeferred<Result<List<StudentPaper>>>()
    val dbEvalByPaperId = mutableMapOf<String, Pair<com.example.data.model.EvaluationDbRecord, List<com.example.data.model.QuestionDbRecord>>?>()
    val edgeFunctionCallPaperIds = java.util.Collections.synchronizedList(mutableListOf<String>())

    val student1 = Student(
      id = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      name = "Student One",
      fatherName = "Father One",
      rollNumber = "R-1",
    )
    val student2 = Student(
      id = "student-race-2",
      classId = "class-race-1",
      teacherId = "teacher-1",
      name = "Student Two",
      fatherName = "Father Two",
      rollNumber = "R-2",
    )
    val classRoom = ClassRoom(
      id = "class-race-1",
      examId = "exam-race-1",
      teacherId = "teacher-1",
      session = "2025-2026",
      semester = "Fall",
      timing = "Morning",
      subjectName = "Physics",
      totalMarks = 20,
    )

    val fakePaperRepo = object : PaperRepository {
      override suspend fun getPapersByStudentId(studentId: String): Result<List<StudentPaper>> {
        return when (studentId) {
          "student-race-1" -> student1PapersDeferred.await()
          "student-race-2" -> student2PapersDeferred.await()
          else -> Result.success(emptyList())
        }
      }

      override suspend fun createPaperWithPages(
        context: Context,
        studentId: String,
        classId: String,
        pageUris: List<Uri>,
        onProgress: (current: Int, total: Int) -> Unit,
      ): Result<StudentPaper> = Result.failure(UnsupportedOperationException())

      override suspend fun getSignedPageUrl(storagePath: String, expiresInSeconds: Long): Result<String> {
        return Result.success("https://example.com/signed/$storagePath")
      }

      override suspend fun deletePaper(paper: StudentPaper): Result<Unit> = Result.success(Unit)
      override suspend fun deletePaperPage(page: StudentPaperPage): Result<Unit> = Result.success(Unit)
    }

    val evalHttpClient = okhttp3.OkHttpClient.Builder()
      .addInterceptor { chain ->
        val bodyBuffer = okio.Buffer()
        chain.request().body?.writeTo(bodyBuffer)
        val reqBodyStr = bodyBuffer.readUtf8()
        val paperIdMatch = Regex(""""paperId"\s*:\s*"([^"]+)"""").find(reqBodyStr)
        val requestedPaperId = paperIdMatch?.groupValues?.get(1) ?: "unknown"
        edgeFunctionCallPaperIds.add(requestedPaperId)

        val bodyJson = """
          {
            "status": "completed",
            "percentageAvailable": true,
            "evaluation": {
              "id": "eval-ai-$requestedPaperId",
              "paperId": "$requestedPaperId",
              "studentId": "student-race-1",
              "classId": "class-race-1",
              "teacherId": "teacher-1",
              "status": "completed",
              "totalMarksObtained": 18.0,
              "totalMarks": 20.0,
              "percentage": 90.0,
              "reviewRequired": false,
              "questions": [
                {
                  "id": "q-ai-1",
                  "evaluation_id": "eval-ai-$requestedPaperId",
                  "question_number": "1",
                  "question_text": "State Ohm's Law.",
                  "maximum_marks": 20.0,
                  "student_answer": "V = I * R at constant temperature.",
                  "expected_answer": "V is proportional to I at constant temperature.",
                  "awarded_marks": 18.0,
                  "feedback": "Accurate answer.",
                  "review_required": false
                }
              ]
            }
          }
        """.trimIndent()

        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(with(okhttp3.ResponseBody) { bodyJson.toResponseBody(null) })
          .build()
      }
      .build()

    val viewModel = StudentDetailViewModel(
      studentRepository = FakeStudentRepository(listOf(student1, student2)),
      classRepository = FakeClassRepository(listOf(classRoom)),
      paperRepository = fakePaperRepo,
      ocrRepository = null,
      evaluationHttpClient = evalHttpClient,
      existingEvaluationDbFetcher = { paperId ->
        dbEvalByPaperId[paperId]
      },
      evaluationAccessTokenProvider = { "test-eval-jwt-token" },
    )

    // -------------------------------------------------------------------------
    // 1. Opening an approved paper in Teacher Review while the paper list is still loading:
    //    ensureEvaluationLoadedForReview must wait for paper list load to finish and then
    //    open the approved evaluation without triggering an AI evaluation call.
    // -------------------------------------------------------------------------
    val approvedPaper1 = StudentPaper(
      id = "paper-approved-1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "approved",
      evaluation = EvaluationStatus(status = "approved"),
    )
    dbEvalByPaperId["paper-approved-1"] = com.example.data.model.EvaluationDbRecord(
      id = "eval-approved-1",
      paperId = "paper-approved-1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      status = "approved",
      totalMarksObtained = 19.0,
      totalMarks = 20.0,
      percentage = 95.0,
      reviewRequired = false,
    ) to listOf(
      com.example.data.model.QuestionDbRecord(
        id = "q-approved-1",
        evaluationId = "eval-approved-1",
        questionNumber = "1",
        questionText = "Define velocity.",
        maximumMarks = 20.0,
        studentAnswer = "Rate of change of displacement.",
        expectedAnswer = "Displacement per unit time.",
        awardedMarks = 19.0,
        feedback = "Well stated.",
        reviewRequired = false,
      )
    )

    // Initial TeacherReviewScreen entry triggers ensureEvaluationLoadedForReview while student/papers are not loaded yet
    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-approved-1")
    waitForCondition { viewModel.uiState.value.isLoading }

    // Calling ensureEvaluationLoadedForReview again while still loading must NOT start AI evaluation
    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-approved-1")
    assertNull(viewModel.uiState.value.isEvaluatingPaperId)
    assertEquals(0, edgeFunctionCallPaperIds.size)

    // Complete the paper list load; simulate TeacherReviewScreen LaunchedEffect re-running on state update
    student1PapersDeferred.complete(Result.success(listOf(approvedPaper1)))
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.papers.size == 1 }

    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-approved-1")
    waitForCondition {
      viewModel.uiState.value.isEvaluatingPaperId == null &&
        viewModel.uiState.value.evaluatedPaperId == "paper-approved-1" &&
        viewModel.uiState.value.evaluationResult != null
    }
    assertEquals(0, edgeFunctionCallPaperIds.size)
    assertEquals("approved", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(19.0, viewModel.uiState.value.evaluationResult?.evaluation?.totalMarksObtained)

    // Also verify direct runAiEvaluation while paper list is loading for an approved paper whose DB record
    // is temporarily missing (NotFound): it waits for loadJob, sees the paper is approved, and refuses to call AI.
    student1PapersDeferred = kotlinx.coroutines.CompletableDeferred()
    val approvedPaperMissingDb = StudentPaper(
      id = "paper-approved-missing-db",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "approved",
      evaluation = EvaluationStatus(status = "approved"),
    )
    dbEvalByPaperId["paper-approved-missing-db"] = null

    viewModel.loadStudentAndPapers("student-race-1")
    waitForCondition { viewModel.uiState.value.isLoading }
    viewModel.runAiEvaluation("paper-approved-missing-db")
    // Complete paper load so runAiEvaluation can inspect the loaded paper status
    student1PapersDeferred.complete(Result.success(listOf(approvedPaperMissingDb)))
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null }

    assertEquals(0, edgeFunctionCallPaperIds.size)
    assertNull(viewModel.uiState.value.evaluationResult)
    assertEquals(
      "Unable to load the saved approved evaluation.",
      viewModel.uiState.value.evaluationError,
    )

    // -------------------------------------------------------------------------
    // 2. Reusing an existing needs_review / completed evaluation without an unnecessary AI request:
    // -------------------------------------------------------------------------
    val needsReviewPaper = StudentPaper(
      id = "paper-needs-review-1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "needs_review",
      evaluation = EvaluationStatus(status = "needs_review"),
    )
    dbEvalByPaperId["paper-needs-review-1"] = com.example.data.model.EvaluationDbRecord(
      id = "eval-needs-review-1",
      paperId = "paper-needs-review-1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      status = "needs_review",
      totalMarksObtained = 14.0,
      totalMarks = 20.0,
      percentage = 70.0,
      reviewRequired = true,
    ) to listOf(
      com.example.data.model.QuestionDbRecord(
        id = "q-nr-1",
        evaluationId = "eval-needs-review-1",
        questionNumber = "1",
        questionText = "Explain inertia.",
        maximumMarks = 20.0,
        studentAnswer = "Resistance to change in motion.",
        expectedAnswer = "Tendency of an object to resist changes in its state of motion.",
        awardedMarks = 14.0,
        feedback = "Needs teacher confirmation.",
        reviewRequired = true,
      )
    )

    student1PapersDeferred = kotlinx.coroutines.CompletableDeferred()
    viewModel.loadStudentAndPapers("student-race-1")
    waitForCondition { viewModel.uiState.value.isLoading }
    student1PapersDeferred.complete(Result.success(listOf(needsReviewPaper)))
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.papers.any { it.id == "paper-needs-review-1" } }

    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-needs-review-1")
    waitForCondition {
      viewModel.uiState.value.isEvaluatingPaperId == null &&
        viewModel.uiState.value.evaluatedPaperId == "paper-needs-review-1" &&
        viewModel.uiState.value.evaluationResult != null
    }
    assertEquals(0, edgeFunctionCallPaperIds.size)
    assertEquals("needs_review", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(14.0, viewModel.uiState.value.evaluationResult?.evaluation?.totalMarksObtained)

    // -------------------------------------------------------------------------
    // 3. Starting evaluation for a genuinely new paper:
    // -------------------------------------------------------------------------
    val brandNewPaper = StudentPaper(
      id = "paper-new-1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "scanned",
      evaluation = null,
    )
    dbEvalByPaperId["paper-new-1"] = null

    student1PapersDeferred = kotlinx.coroutines.CompletableDeferred()
    viewModel.loadStudentAndPapers("student-race-1")
    waitForCondition { viewModel.uiState.value.isLoading }

    // While still loading, ensureEvaluationLoadedForReview waits
    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-new-1")
    assertEquals(0, edgeFunctionCallPaperIds.size)

    student1PapersDeferred.complete(Result.success(listOf(brandNewPaper)))
    waitForCondition { !viewModel.uiState.value.isLoading && viewModel.uiState.value.papers.any { it.id == "paper-new-1" } }

    // Once loaded, ensureEvaluationLoadedForReview runs AI evaluation for the genuinely new paper
    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-1", paperId = "paper-new-1")
    waitForCondition {
      viewModel.uiState.value.isEvaluatingPaperId == null &&
        viewModel.uiState.value.evaluatedPaperId == "paper-new-1" &&
        viewModel.uiState.value.evaluationResult != null
    }
    assertEquals(listOf("paper-new-1"), edgeFunctionCallPaperIds.toList())
    assertEquals("completed", viewModel.uiState.value.evaluationResult?.evaluation?.status)
    assertEquals(18.0, viewModel.uiState.value.evaluationResult?.evaluation?.totalMarksObtained)

    // -------------------------------------------------------------------------
    // 4. Preventing stale loading results from triggering evaluation for the wrong paper:
    // -------------------------------------------------------------------------
    edgeFunctionCallPaperIds.clear()
    student1PapersDeferred = kotlinx.coroutines.CompletableDeferred()
    student2PapersDeferred = kotlinx.coroutines.CompletableDeferred()

    val staleStudent1Paper = StudentPaper(
      id = "paper-stale-s1",
      studentId = "student-race-1",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "scanned",
      evaluation = null,
    )
    val activeStudent2Paper = StudentPaper(
      id = "paper-active-s2",
      studentId = "student-race-2",
      classId = "class-race-1",
      teacherId = "teacher-1",
      totalPages = 1,
      status = "approved",
      evaluation = EvaluationStatus(status = "approved"),
    )
    dbEvalByPaperId["paper-stale-s1"] = null
    dbEvalByPaperId["paper-active-s2"] = com.example.data.model.EvaluationDbRecord(
      id = "eval-s2",
      paperId = "paper-active-s2",
      studentId = "student-race-2",
      classId = "class-race-1",
      teacherId = "teacher-1",
      status = "approved",
      totalMarksObtained = 20.0,
      totalMarks = 20.0,
      percentage = 100.0,
      reviewRequired = false,
    ) to emptyList()

    // Start loading student-race-1, then immediately switch to student-race-2 before student-race-1 finishes
    viewModel.loadStudentAndPapers("student-race-1")
    viewModel.loadStudentAndPapers("student-race-2")

    // Complete stale student-race-1 first, then active student-race-2
    student1PapersDeferred.complete(Result.success(listOf(staleStudent1Paper)))
    student2PapersDeferred.complete(Result.success(listOf(activeStudent2Paper)))

    waitForCondition {
      !viewModel.uiState.value.isLoading &&
        viewModel.uiState.value.student?.id == "student-race-2" &&
        viewModel.uiState.value.papers.map { it.id } == listOf("paper-active-s2")
    }

    // Attempting to review or evaluate staleStudent1Paper under student-race-2 must NOT call AI
    viewModel.ensureEvaluationLoadedForReview(studentId = "student-race-2", paperId = "paper-stale-s1")
    assertEquals(0, edgeFunctionCallPaperIds.size)

    viewModel.runAiEvaluation("paper-stale-s1")
    waitForCondition { viewModel.uiState.value.isEvaluatingPaperId == null }
    assertEquals(0, edgeFunctionCallPaperIds.size)
    assertEquals("Selected paper could not be found.", viewModel.uiState.value.evaluationError)
  }

  @Test
  fun `verify homeDashboardClassCount single count query eliminates per-exam loop handles zero counts and errors`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val exam1 = Exam(id = "exam-1", teacherId = "teacher-1", name = "Math Midterm", type = "Mid Term", date = "2026-10-01")
    val exam2 = Exam(id = "exam-2", teacherId = "teacher-1", name = "Physics Quiz", type = "Quiz", date = "2026-10-02")
    val exam3 = Exam(id = "exam-3", teacherId = "teacher-1", name = "Chemistry Final", type = "Final", date = "2026-10-03")

    val class1 = ClassRoom(id = "class-1", examId = "exam-1", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Math 101", totalMarks = 100)
    val class2 = ClassRoom(id = "class-2", examId = "exam-1", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Evening", subjectName = "Math 102", totalMarks = 100)
    val class3 = ClassRoom(id = "class-3", examId = "exam-2", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Physics 101", totalMarks = 100)
    val class4 = ClassRoom(id = "class-4", examId = "exam-3", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Chem 101", totalMarks = 100)
    val class5 = ClassRoom(id = "class-5", examId = "exam-3", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Evening", subjectName = "Chem 102", totalMarks = 100)

    val fakeAuthRepo = FakeAuthRepository(signedInUser = "teacher@school.edu", customUserId = "teacher-1")
    val fakeExamRepo = FakeExamRepository(initialExams = listOf(exam1, exam2, exam3))
    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(class1, class2, class3, class4, class5))

    // 1. Multiple exams and classes: loads with a single count query, 0 per-exam queries
    val homeViewModel = HomeViewModel(fakeAuthRepo, fakeExamRepo, fakeClassRepo)
    mainLooper.idle()

    assertFalse(homeViewModel.uiState.value.isLoading)
    assertNull(homeViewModel.uiState.value.errorMessage)
    assertEquals(3, homeViewModel.uiState.value.examCount)
    assertEquals(5, homeViewModel.uiState.value.classCount)
    assertEquals(1, fakeExamRepo.getExamsCallCount)
    assertEquals(1, fakeClassRepo.getClassCountCallCount)
    assertEquals(0, fakeClassRepo.getClassesCallCount) // ZERO per-exam calls!

    // 2. Zero classes with existing exams
    val fakeClassRepoEmpty = FakeClassRepository(initialClasses = emptyList())
    val homeViewModelEmptyClasses = HomeViewModel(fakeAuthRepo, fakeExamRepo, fakeClassRepoEmpty)
    mainLooper.idle()

    assertFalse(homeViewModelEmptyClasses.uiState.value.isLoading)
    assertNull(homeViewModelEmptyClasses.uiState.value.errorMessage)
    assertEquals(3, homeViewModelEmptyClasses.uiState.value.examCount)
    assertEquals(0, homeViewModelEmptyClasses.uiState.value.classCount)
    assertEquals(1, fakeClassRepoEmpty.getClassCountCallCount)
    assertEquals(0, fakeClassRepoEmpty.getClassesCallCount)

    // 3. Zero exams and zero classes
    val fakeExamRepoEmpty = FakeExamRepository(initialExams = emptyList())
    val homeViewModelEmptyAll = HomeViewModel(fakeAuthRepo, fakeExamRepoEmpty, fakeClassRepoEmpty)
    mainLooper.idle()

    assertFalse(homeViewModelEmptyAll.uiState.value.isLoading)
    assertNull(homeViewModelEmptyAll.uiState.value.errorMessage)
    assertEquals(0, homeViewModelEmptyAll.uiState.value.examCount)
    assertEquals(0, homeViewModelEmptyAll.uiState.value.classCount)

    // 4. Zero exams with non-empty classes in db: exam list empty guarantees classCount = 0
    val homeViewModelZeroExamsNonEmptyClasses = HomeViewModel(fakeAuthRepo, fakeExamRepoEmpty, fakeClassRepo)
    mainLooper.idle()

    assertFalse(homeViewModelZeroExamsNonEmptyClasses.uiState.value.isLoading)
    assertNull(homeViewModelZeroExamsNonEmptyClasses.uiState.value.errorMessage)
    assertEquals(0, homeViewModelZeroExamsNonEmptyClasses.uiState.value.examCount)
    assertEquals(0, homeViewModelZeroExamsNonEmptyClasses.uiState.value.classCount)

    // 5. Class count query failure: reports error gracefully, retains examCount
    val fakeClassRepoFailing = FakeClassRepository(initialClasses = listOf(class1, class2))
    fakeClassRepoFailing.shouldFail = true
    val homeViewModelFailingClass = HomeViewModel(fakeAuthRepo, fakeExamRepo, fakeClassRepoFailing)
    mainLooper.idle()

    assertFalse(homeViewModelFailingClass.uiState.value.isLoading)
    assertNull(homeViewModelFailingClass.uiState.value.errorMessage)
    assertEquals(3, homeViewModelFailingClass.uiState.value.examCount)
    assertEquals(0, homeViewModelFailingClass.uiState.value.classCount)

    // 6. Suspended request cancellation & account switching
    val deferredExams = kotlinx.coroutines.CompletableDeferred<Result<List<Exam>>>()
    val deferredClassCount = kotlinx.coroutines.CompletableDeferred<Result<Int>>()
    val fakeExamRepoSuspended = FakeExamRepository(initialExams = listOf(exam1), deferredExamsResult = deferredExams)
    val fakeClassRepoSuspended = FakeClassRepository(initialClasses = listOf(class1))
    fakeClassRepoSuspended.deferredClassCountResult = deferredClassCount

    val homeViewModelSuspended = HomeViewModel(fakeAuthRepo, fakeExamRepoSuspended, fakeClassRepoSuspended)
    mainLooper.idle()
    assertTrue(homeViewModelSuspended.uiState.value.isLoading)
    assertEquals(1, fakeExamRepoSuspended.getExamsCallCount)
    assertEquals(1, fakeClassRepoSuspended.getClassCountCallCount)

    // Deduplication while in-flight
    homeViewModelSuspended.loadUserData(forceRefresh = false)
    mainLooper.idle()
    assertEquals(1, fakeExamRepoSuspended.getExamsCallCount)
    assertEquals(1, fakeClassRepoSuspended.getClassCountCallCount)

    // Switch account before completing
    fakeAuthRepo.customUserId = "other-teacher"
    deferredExams.complete(Result.success(listOf(exam1)))
    deferredClassCount.complete(Result.success(1))
    mainLooper.idle()

    assertFalse(homeViewModelSuspended.uiState.value.isLoading)
    // Stale result discarded because account switched
    assertEquals(0, homeViewModelSuspended.uiState.value.examCount)
    assertEquals(0, homeViewModelSuspended.uiState.value.classCount)
  }

  @Test
  fun `verify ClassViewModel loadClasses handles exam switching late responses deduplication cancellation and errors`() = runTest {
    val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
    val classA1 = ClassRoom(id = "class-A1", examId = "exam-A", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Algebra", totalMarks = 100)
    val classB1 = ClassRoom(id = "class-B1", examId = "exam-B", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Morning", subjectName = "Biology", totalMarks = 100)
    val classB2 = ClassRoom(id = "class-B2", examId = "exam-B", teacherId = "teacher-1", session = "2026", semester = "Fall", timing = "Evening", subjectName = "Biochemistry", totalMarks = 100)

    val fakeClassRepo = FakeClassRepository(initialClasses = listOf(classA1, classB1, classB2))
    fakeClassRepo.ignoreCancellationOnDeferred = true // Simulate delayed completion after job cancellation
    val classViewModel = ClassViewModel(fakeClassRepo)

    val deferredA = kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>()
    val deferredB = kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>()
    fakeClassRepo.deferredClassesByExamId["exam-A"] = deferredA
    fakeClassRepo.deferredClassesByExamId["exam-B"] = deferredB

    // 1. Initial request for Exam A suspends on deferredA
    classViewModel.loadClasses("exam-A")
    mainLooper.idle()
    assertTrue(classViewModel.uiState.value.isLoading)
    assertEquals("exam-A", classViewModel.uiState.value.activeExamId)
    assertEquals(1, fakeClassRepo.getClassesCallCount)

    // 2. Duplicate in-flight request for Exam A without forceRefresh is deduplicated
    classViewModel.loadClasses("exam-A", forceRefresh = false)
    mainLooper.idle()
    assertEquals(1, fakeClassRepo.getClassesCallCount) // Call count did not increase!

    // 3. Teacher switches quickly to Exam B while Exam A is still suspended
    classViewModel.loadClasses("exam-B")
    mainLooper.idle()
    assertTrue(classViewModel.uiState.value.isLoading)
    assertEquals("exam-B", classViewModel.uiState.value.activeExamId)
    assertTrue(classViewModel.uiState.value.classes.isEmpty()) // Switching exams clears obsolete class list
    assertEquals(2, fakeClassRepo.getClassesCallCount)

    // 4. Exam B completes successfully first
    deferredB.complete(Result.success(listOf(classB1, classB2)))
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertNull(classViewModel.uiState.value.errorMessage)
    assertEquals("exam-B", classViewModel.uiState.value.activeExamId)
    assertEquals(2, classViewModel.uiState.value.classes.size)
    assertEquals(listOf("class-B1", "class-B2"), classViewModel.uiState.value.classes.map { it.id })

    // 5. Exam A completes LATE after Exam B — its obsolete response must NOT overwrite Exam B's state
    deferredA.complete(Result.success(listOf(classA1)))
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertNull(classViewModel.uiState.value.errorMessage)
    assertEquals("exam-B", classViewModel.uiState.value.activeExamId)
    assertEquals(2, classViewModel.uiState.value.classes.size) // Still Exam B's classes!
    assertEquals(listOf("class-B1", "class-B2"), classViewModel.uiState.value.classes.map { it.id })

    // 6. Force refresh on Exam B while idle starts a new load
    fakeClassRepo.deferredClassesByExamId.remove("exam-B")
    val deferredBRefresh = kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>()
    fakeClassRepo.deferredClassesByExamId["exam-B"] = deferredBRefresh
    classViewModel.loadClasses("exam-B", forceRefresh = true)
    mainLooper.idle()
    assertTrue(classViewModel.uiState.value.isLoading)
    assertEquals(3, fakeClassRepo.getClassesCallCount)

    // Starting another forceRefresh cancels the previous one
    val deferredBRefresh2 = kotlinx.coroutines.CompletableDeferred<Result<List<ClassRoom>>>()
    fakeClassRepo.deferredClassesByExamId["exam-B"] = deferredBRefresh2
    classViewModel.loadClasses("exam-B", forceRefresh = true)
    mainLooper.idle()
    assertEquals(4, fakeClassRepo.getClassesCallCount)

    deferredBRefresh2.complete(Result.success(listOf(classB1)))
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertEquals(1, classViewModel.uiState.value.classes.size)

    // 7. Genuine repository failure reports error and does NOT leave screen permanently loading
    fakeClassRepo.deferredClassesByExamId.clear()
    fakeClassRepo.shouldFail = true
    classViewModel.loadClasses("exam-C", forceRefresh = true)
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertEquals("Failed to fetch classes", classViewModel.uiState.value.errorMessage)
    assertEquals("exam-C", classViewModel.uiState.value.activeExamId)

    // 8. CancellationException does NOT display as an ordinary error message
    fakeClassRepo.shouldFail = false
    fakeClassRepo.throwCancellation = true
    classViewModel.loadClasses("exam-D", forceRefresh = true)
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertNull(classViewModel.uiState.value.errorMessage)

    // 9. Retry after cancellation recovers cleanly
    fakeClassRepo.throwCancellation = false
    classViewModel.loadClasses("exam-B", forceRefresh = true)
    mainLooper.idle()
    assertFalse(classViewModel.uiState.value.isLoading)
    assertNull(classViewModel.uiState.value.errorMessage)
    assertEquals(2, classViewModel.uiState.value.classes.size)
  }
}





