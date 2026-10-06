package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ClassRoom
import com.example.data.model.ClassTiming
import com.example.data.model.Exam
import com.example.data.model.ExamType
import com.example.data.model.Student
import com.example.data.model.StudentPaper
import com.example.data.model.StudentPaperPage
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

class FakeExamRepository(
  initialExams: List<Exam> = emptyList(),
  var shouldFail: Boolean = false,
) : ExamRepository {
  private val exams = initialExams.toMutableList()

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
    return if (shouldFail) {
      Result.failure(Exception("Failed to delete exam"))
    } else {
      exams.removeAll { it.id == id }
      Result.success(Unit)
    }
  }
}

class FakeClassRepository(
  initialClasses: List<ClassRoom> = emptyList(),
  var shouldFail: Boolean = false,
) : ClassRepository {
  private val classes = initialClasses.toMutableList()

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
    return if (shouldFail) {
      Result.failure(Exception("Failed to delete class"))
    } else {
      classes.removeAll { it.id == id }
      Result.success(Unit)
    }
  }
}

class FakeStudentRepository(
  initialStudents: List<Student> = emptyList(),
  var shouldFail: Boolean = false,
) : StudentRepository {
  private val students = initialStudents.toMutableList()

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
    return if (shouldFail) {
      Result.failure(Exception("Failed to delete student"))
    } else {
      val removed = students.removeAll { it.id == studentId }
      if (removed) {
        Result.success(Unit)
      } else {
        Result.failure(IllegalStateException("Student not found"))
      }
    }
  }
}

class FakePaperRepository(
  initialPapers: List<StudentPaper> = emptyList(),
  var shouldFail: Boolean = false,
) : PaperRepository {
  private val papers = initialPapers.toMutableList()
  val deletedStoragePaths = mutableListOf<String>()

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
    return if (shouldFail) {
      Result.failure(Exception("Failed to delete paper"))
    } else {
      papers.removeAll { it.id == paper.id }
      deletedStoragePaths.addAll(paper.pages.map { it.storagePath })
      Result.success(Unit)
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
  }
}
