package com.example.ui.navigation

sealed class Screen(val route: String) {
  data object Splash : Screen("splash")
  data object Login : Screen("login")
  data object SignUp : Screen("sign_up")
  data object Home : Screen("home")
  data object Exams : Screen("exams")
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
}
