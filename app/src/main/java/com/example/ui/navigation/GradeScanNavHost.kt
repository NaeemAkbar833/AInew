package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClassRepository
import com.example.data.repository.ExamRepository
import com.example.data.repository.OcrRepository
import com.example.data.repository.PaperRepository
import com.example.data.repository.StudentRepository
import com.example.data.repository.SupabaseAuthRepository
import com.example.data.repository.SupabaseClassRepository
import com.example.data.repository.SupabaseExamRepository
import com.example.data.repository.SupabaseOcrRepository
import com.example.data.repository.SupabasePaperRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.SignUpScreen
import com.example.ui.classes.ClassViewModel
import com.example.ui.exam.CreateExamDialog
import com.example.ui.exam.ExamDetailScreen
import com.example.ui.exam.ExamViewModel
import com.example.ui.exam.ExamsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.results.ResultsScreen
import com.example.ui.results.ExamClassesResultsScreen
import com.example.ui.results.ClassResultsScreen
import com.example.ui.results.ClassResultsViewModel
import com.example.ui.results.StudentResultDetailScreen
import com.example.ui.results.StudentResultDetailViewModel
import com.example.ui.results.ClassReportScreen
import com.example.ui.results.ClassReportViewModel
import com.example.ui.paper.ScanPaperScreen
import com.example.ui.paper.ScanPaperViewModel
import com.example.ui.paper.StudentDetailScreen
import com.example.ui.paper.StudentDetailViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.student.ClassDetailScreen
import com.example.ui.student.StudentViewModel

@Composable
fun GradeScanNavHost(
  modifier: Modifier = Modifier,
  navController: NavHostController = rememberNavController(),
  authRepository: AuthRepository = remember { SupabaseAuthRepository() },
  examRepository: ExamRepository = remember { SupabaseExamRepository(authRepository) },
  classRepository: ClassRepository = remember { SupabaseClassRepository(authRepository) },
  studentRepository: StudentRepository = remember { SupabaseStudentRepository(authRepository) },
  paperRepository: PaperRepository = remember { SupabasePaperRepository(authRepository) },
  ocrRepository: OcrRepository = remember { SupabaseOcrRepository(authRepository) },
) {
  NavHost(
    navController = navController,
    startDestination = Screen.Splash.route,
    modifier = modifier,
  ) {
    composable(Screen.Splash.route) {
      SplashScreen(
        authRepository = authRepository,
        onNavigateToHome = {
          navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
          }
        },
        onNavigateToLogin = {
          navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
          }
        },
      )
    }

    composable(Screen.Login.route) {
      val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
      LoginScreen(
        viewModel = authViewModel,
        onNavigateToSignUp = {
          authViewModel.clearError()
          navController.navigate(Screen.SignUp.route)
        },
        onLoginSuccess = {
          authViewModel.resetSuccess()
          navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
          }
        },
      )
    }

    composable(Screen.SignUp.route) {
      val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
      SignUpScreen(
        viewModel = authViewModel,
        onNavigateBack = {
          authViewModel.clearError()
          navController.popBackStack()
        },
        onSignUpSuccess = {
          authViewModel.resetSuccess()
          navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
          }
        },
      )
    }

    composable(Screen.Home.route) {
      val homeViewModel: HomeViewModel = viewModel { HomeViewModel(authRepository) }
      var isHomeCreateExamDialogOpen by remember { mutableStateOf(false) }

      HomeScreen(
        viewModel = homeViewModel,
        onSignedOut = {
          navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
          }
        },
        onNavigateToExams = {
          navController.navigate(Screen.Exams.route)
        },
        onNavigateToResults = {
          navController.navigate(Screen.Results.route)
        },
        onOpenCreateExam = {
          isHomeCreateExamDialogOpen = true
        },
      )

      if (isHomeCreateExamDialogOpen) {
        val examViewModel: ExamViewModel = viewModel { ExamViewModel(examRepository) }
        CreateExamDialog(
          onDismissRequest = { isHomeCreateExamDialogOpen = false },
          onExamCreated = { newExam ->
            isHomeCreateExamDialogOpen = false
            navController.navigate(Screen.Exams.route)
          },
          viewModel = examViewModel,
        )
      }
    }

    composable(Screen.Exams.route) {
      val examViewModel: ExamViewModel = viewModel { ExamViewModel(examRepository) }
      ExamsScreen(
        viewModel = examViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onExamClick = { examId ->
          navController.navigate(Screen.ExamDetail.createRoute(examId))
        },
      )
    }

    composable(Screen.Results.route) {
      val examViewModel: ExamViewModel = viewModel { ExamViewModel(examRepository) }
      ResultsScreen(
        viewModel = examViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onExamClick = { examId ->
          navController.navigate(Screen.ExamClassesResults.createRoute(examId))
        },
      )
    }

    composable(
      route = Screen.ExamClassesResults.route,
      arguments = listOf(navArgument("examId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val examId = backStackEntry.arguments?.getString("examId") ?: ""
      val examViewModel: ExamViewModel = viewModel { ExamViewModel(examRepository) }
      val classViewModel: ClassViewModel = viewModel { ClassViewModel(classRepository) }
      ExamClassesResultsScreen(
        examId = examId,
        examViewModel = examViewModel,
        classViewModel = classViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onClassClick = { classId ->
          navController.navigate(Screen.ClassResults.createRoute(classId))
        },
      )
    }

    composable(
      route = Screen.ClassResults.route,
      arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val classId = backStackEntry.arguments?.getString("classId") ?: ""
      val classResultsViewModel: ClassResultsViewModel = viewModel {
        ClassResultsViewModel(classRepository, studentRepository)
      }
      ClassResultsScreen(
        classId = classId,
        viewModel = classResultsViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onStudentClick = { studentId ->
          navController.navigate(Screen.StudentResultDetail.createRoute(studentId, classId))
        },
        onClassReportClick = {
          navController.navigate(Screen.ClassReport.createRoute(classId))
        },
      )
    }

    composable(
      route = Screen.ClassReport.route,
      arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val classId = backStackEntry.arguments?.getString("classId") ?: ""
      val classReportViewModel: ClassReportViewModel = viewModel {
        ClassReportViewModel(classRepository, examRepository, studentRepository)
      }
      ClassReportScreen(
        classId = classId,
        viewModel = classReportViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
      )
    }

    composable(
      route = Screen.StudentResultDetail.route,
      arguments = listOf(
        navArgument("studentId") { type = NavType.StringType },
        navArgument("classId") { type = NavType.StringType },
      ),
    ) { backStackEntry ->
      val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
      val classId = backStackEntry.arguments?.getString("classId") ?: ""
      val studentResultDetailViewModel: StudentResultDetailViewModel = viewModel {
        StudentResultDetailViewModel(studentRepository, classRepository)
      }
      StudentResultDetailScreen(
        studentId = studentId,
        classId = classId,
        viewModel = studentResultDetailViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
      )
    }

    composable(
      route = Screen.ExamDetail.route,
      arguments = listOf(navArgument("examId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val examId = backStackEntry.arguments?.getString("examId") ?: ""
      val examViewModel: ExamViewModel = viewModel { ExamViewModel(examRepository) }
      val classViewModel: ClassViewModel = viewModel { ClassViewModel(classRepository) }
      ExamDetailScreen(
        examId = examId,
        examViewModel = examViewModel,
        classViewModel = classViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onClassClick = { classId ->
          navController.navigate(Screen.ClassDetail.createRoute(classId))
        },
      )
    }

    composable(
      route = Screen.ClassDetail.route,
      arguments = listOf(navArgument("classId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val classId = backStackEntry.arguments?.getString("classId") ?: ""
      val studentViewModel: StudentViewModel = viewModel { StudentViewModel(studentRepository, classRepository) }
      ClassDetailScreen(
        classId = classId,
        viewModel = studentViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onStudentClick = { studentId ->
          navController.navigate(Screen.StudentDetail.createRoute(studentId))
        },
      )
    }

    composable(
      route = Screen.StudentDetail.route,
      arguments = listOf(navArgument("studentId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
      val studentDetailViewModel: StudentDetailViewModel = viewModel {
        StudentDetailViewModel(studentRepository, classRepository, paperRepository, ocrRepository)
      }

      StudentDetailScreen(
        studentId = studentId,
        viewModel = studentDetailViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onNavigateToScan = { sid, cid ->
          navController.navigate(Screen.ScanPaper.createRoute(sid, cid))
        },
      )
    }

    composable(
      route = Screen.ScanPaper.route,
      arguments = listOf(
        navArgument("studentId") { type = NavType.StringType },
        navArgument("classId") { type = NavType.StringType },
      ),
    ) { backStackEntry ->
      val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
      val classId = backStackEntry.arguments?.getString("classId") ?: ""
      val scanPaperViewModel: ScanPaperViewModel = viewModel { ScanPaperViewModel(paperRepository) }

      // Access the StudentDetailViewModel from the previous backstack entry (StudentDetail route)
      // This must be called in a @Composable context.
      val previousEntry = remember(backStackEntry) { navController.previousBackStackEntry }
      val studentDetailViewModel: StudentDetailViewModel? = previousEntry?.let { viewModel(it) }

      ScanPaperScreen(
        studentId = studentId,
        classId = classId,
        viewModel = scanPaperViewModel,
        onNavigateBack = {
          navController.popBackStack()
        },
        onScanFinished = { paperId ->
          studentDetailViewModel?.let { vm ->
            vm.loadStudentAndPapers(studentId)
          }
          navController.popBackStack()
        },
      )
    }
  }
}
