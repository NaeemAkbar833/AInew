package com.example.ui.navigation

sealed class Screen(val route: String) {
  data object Splash : Screen("splash")
  data object Login : Screen("login")
  data object SignUp : Screen("sign_up")
  data object Home : Screen("home")
  data object Exams : Screen("exams")
  data object Classes : Screen("classes")
  data object Results : Screen("results")
  data object ExamClassesResults : Screen("results_exam_classes/{examId}") {
    fun createRoute(examId: String) = "results_exam_classes/$examId"
  }
  data object ClassResults : Screen("results_class/{classId}") {
    fun createRoute(classId: String) = "results_class/$classId"
  }
  data object StudentResultDetail : Screen("student_result_detail/{studentId}/{classId}") {
    fun createRoute(studentId: String, classId: String) = "student_result_detail/$studentId/$classId"
  }
  data object ClassReport : Screen("class_report/{classId}") {
    fun createRoute(classId: String) = "class_report/$classId"
  }
  data object ExamDetail : Screen("exam_detail/{examId}") {
    fun createRoute(examId: String) = "exam_detail/$examId"
  }
  data object ClassDetail : Screen("class_detail/{classId}") {
    fun createRoute(classId: String) = "class_detail/$classId"
  }
  data object StudentDetail : Screen("student_detail/{studentId}") {
    fun createRoute(studentId: String) = "student_detail/$studentId"
  }
  data object ScanPaper : Screen("scan_paper/{studentId}/{classId}") {
    fun createRoute(studentId: String, classId: String) = "scan_paper/$studentId/$classId"
  }
  data object TeacherReview : Screen("teacher_review/{studentId}/{paperId}") {
    fun createRoute(studentId: String, paperId: String) = "teacher_review/$studentId/$paperId"
  }
}
