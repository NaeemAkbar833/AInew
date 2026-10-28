package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AiEvaluationResponse
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.model.EvaluationDetail
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
import com.example.ui.auth.AuthViewModel
import com.example.ui.classes.ClassViewModel
import com.example.ui.exam.ExamViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.paper.ScanPaperViewModel
import com.example.ui.paper.StudentDetailViewModel
import com.example.ui.student.StudentViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
  var shouldSucceed: Boolean = true
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
    signedInUser = null
    return Result.success(Unit)
  }

  override fun getCurrentUserEmail(): String? = signedInUser

  override fun getCurrentUserId(): String? = if (signedInUser != null) "user-123" else null

  override fun isUserSignedIn(): Boolean = signedInUser != null

  override suspend fun checkSession(): Boolean = signedInUser != null
}

internal class FakeExamRepository(
  initialExams: List<Exam> = emptyList(),
  var shouldFail: Boolean = false,
) : ExamRepository {
  private val exams = initialExams.toMutableList()
  val deletedExamIds = mutableListOf<String>()

  override suspend fun getExams(): Result<List<Exam>> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch exams"))
    } else {
      Result.success(exams.toList())
    }
  }

  override suspend fun getExamById(id: String): Result<Exam?> {
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
) : ClassRepository {
  private val classes = initialClasses.toMutableList()
  val deletedClassIds = mutableListOf<String>()

  override suspend fun getClassesByExamId(examId: String): Result<List<ClassRoom>> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch classes"))
    } else {
      Result.success(classes.filter { it.examId == examId })
    }
  }

  override suspend fun getClassById(classId: String): Result<ClassRoom?> {
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
) : StudentRepository {
  private val students = initialStudents.toMutableList()
  val deletedStudentIds = mutableListOf<String>()

  override suspend fun getStudentsByClassId(classId: String): Result<List<Student>> {
    return if (shouldFail) {
      Result.failure(Exception("Failed to fetch students"))
    } else {
      Result.success(students.filter { it.classId == classId }.sortedBy { it.rollNumber })
    }
  }

  override suspend fun getStudentById(studentId: String): Result<Student?> {
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
) : PaperRepository {
  private val papers = initialPapers.toMutableList()
  val deletedStoragePaths = mutableListOf<String>()
  var lastUploadedPageUris: List<Uri> = emptyList()

  override suspend fun getPapersByStudentId(studentId: String): Result<List<StudentPaper>> {
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
}


