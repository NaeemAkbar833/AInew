package com.example.ui.paper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.LocalScannedPage
import com.example.ui.components.GradeScanProgressDialog
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import kotlinx.coroutines.launch

/**
 * Screen for capturing or picking multiple pages of a student's answer sheet,
 * previewing them in sequence, removing individual pages, and uploading
 * them to Supabase Storage (`student-papers` bucket) + creating database records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPaperScreen(
  studentId: String,
  classId: String,
  viewModel: ScanPaperViewModel,
  onNavigateBack: () -> Unit,
  onScanFinished: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  LaunchedEffect(studentId) {
    viewModel.loadStudentInfo(studentId)
  }

  // Show upload errors in snackbar
  LaunchedEffect(uiState.uploadError) {
    uiState.uploadError?.let { errorMsg ->
      snackbarHostState.showSnackbar(errorMsg)
      viewModel.clearError()
    }
  }

  // Camera capture launcher
  val takePictureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    val uri = pendingCameraUri
    if (success && uri != null) {
      viewModel.addPage(uri)
    }
  }

  // Helper to launch camera with a fresh temp file
  val launchCameraCapture = {
    try {
      val (_, uri) = createTempImageFileAndUri(context)
      pendingCameraUri = uri
      takePictureLauncher.launch(uri)
    } catch (e: Exception) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Unable to start camera: ${e.localizedMessage}")
      }
    }
  }

  // Runtime Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      launchCameraCapture()
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Camera permission is required to capture answer sheets.")
      }
    }
  }

  // Gallery Picker for adding one or multiple pages at once
  val addMultipleGalleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.addPages(uris)
    }
  }

  // Direct handlers for Camera and Gallery actions
  val onTakeCameraPhoto: () -> Unit = {
    val hasCameraPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
    if (hasCameraPermission) {
      launchCameraCapture()
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  val onChooseFromGallery: () -> Unit = {
    val pickRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    addMultipleGalleryLauncher.launch(pickRequest)
  }

  val topHeaderBlendGradient = remember {
    Brush.verticalGradient(
      colorStops = arrayOf(
        0.00f to DarkRoyalBlue,
        0.36f to PrimaryBlue,
        0.58f to Color(0xFF3358DF),
        0.76f to Color(0xFF6E8CEF),
        0.90f to Color(0xFFC2D3FA),
        1.00f to BackgroundLight,
      )
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundLight)
      .testTag("scan_paper_screen"),
  ) {
    // Backdrop gradient layer extending behind topBar down into upper content area
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(240.dp)
        .background(topHeaderBlendGradient),
    )

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(HeaderBlueGradient)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)
            .testTag("top_app_bar"),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clickable(
                    role = Role.Button,
                    onClick = {
                      if (!uiState.isUploading) {
                        onNavigateBack()
                      }
                    },
                  )
                  .testTag("scan_paper_back_button"),
                contentAlignment = Alignment.Center,
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(
                      width = 1.dp,
                      color = Color.White.copy(alpha = 0.30f),
                      shape = CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = "Scan Answer Sheet",
                  fontFamily = BricolageGrotesqueFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Capture or select pages in order",
                  fontFamily = FigtreeFontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 12.sp,
                  color = Color.White.copy(alpha = 0.85f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              if (uiState.pages.isNotEmpty()) {
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = Color.White.copy(alpha = 0.2f),
                  border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                  modifier = Modifier.padding(end = 6.dp),
                ) {
                  Text(
                    text = "${uiState.pages.size} ${if (uiState.pages.size == 1) "Page" else "Pages"}",
                    fontSize = 11.5.sp,
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (uiState.pages.isNotEmpty()) {
          val progressText = uiState.uploadProgress?.let { (cur, total) ->
            "Uploading $cur/$total..."
          }
          BottomScanActionBar(
            pageCount = uiState.pages.size,
            isUploading = uiState.isUploading,
            uploadProgressText = progressText,
            onTakePhotoClick = onTakeCameraPhoto,
            onChooseGalleryClick = onChooseFromGallery,
            onSaveClick = {
              viewModel.finishScan(
                context = context,
                studentId = studentId,
                classId = classId,
                onSuccess = onScanFinished,
              )
            },
          )
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
        ) {
          if (uiState.pages.isEmpty()) {
            // Empty state with clear context and two direct actions
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              if (!uiState.studentName.isNullOrBlank()) {
                StudentContextBanner(
                  studentName = uiState.studentName!!,
                  rollNumber = uiState.studentRollNumber,
                )
              }

              InstructionBanner()

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f),
                contentAlignment = Alignment.Center,
              ) {
                EmptyPagesState(
                  onTakePhoto = onTakeCameraPhoto,
                  onChooseGallery = onChooseFromGallery,
                )
              }
            }
          } else {
            // Multi-page preview grid
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 148.dp),
              contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 24.dp,
              ),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxSize()
                .testTag("scan_pages_grid"),
            ) {
              if (!uiState.studentName.isNullOrBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  StudentContextBanner(
                    studentName = uiState.studentName!!,
                    rollNumber = uiState.studentRollNumber,
                  )
                }
              }

              item(span = { GridItemSpan(maxLineSpan) }) {
                InstructionBanner()
              }

              items(
                items = uiState.pages,
                key = { it.id },
              ) { page ->
                ScannedPageCard(
                  page = page,
                  isUploading = uiState.isUploading,
                  onPreview = { viewModel.setPreviewPageUri(page.uri) },
                  onRemove = { viewModel.removePage(page.id) },
                )
              }
            }
          }
        }

        // Upload progress dialog
        if (uiState.isUploading) {
          val progressSubtitle = uiState.uploadProgress?.let { (cur, total) ->
            "Uploading page $cur of $total to cloud storage..."
          } ?: "Saving answer sheet pages to cloud storage..."

          GradeScanProgressDialog(
            title = "Uploading Answer Sheet",
            subtitle = progressSubtitle,
          )
        }
      }
    }
  }

  // Fullscreen page preview dialog
  uiState.previewPageUri?.let { previewUri ->
    ScannedPagePreviewDialog(
      uri = previewUri,
      onDismiss = { viewModel.setPreviewPageUri(null) },
    )
  }
}

@Composable
private fun StudentContextBanner(
  studentName: String,
  rollNumber: String?,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(LightBlueHighlight)
          .border(1.dp, RoyalBlue.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(18.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Scanning for $studentName",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
        if (!rollNumber.isNullOrBlank()) {
          Text(
            text = "Roll No: $rollNumber",
            fontFamily = FigtreeFontFamily,
            fontSize = 12.sp,
            color = TextSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun InstructionBanner() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = LightBlueHighlight),
    border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.2f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color.White)
          .border(1.dp, RoyalBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.DocumentScanner,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(18.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Multi-Page Capture",
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Add pages in sequence (Page 1, Page 2, etc.). Ensure handwriting is clear and well-lit for accurate OCR.",
          fontFamily = FigtreeFontFamily,
          fontSize = 12.sp,
          lineHeight = 16.sp,
          color = TextSecondary,
        )
      }
    }
  }
}

@Composable
private fun EmptyPagesState(
  onTakePhoto: () -> Unit,
  onChooseGallery: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(LightBlueHighlight),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.DocumentScanner,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(22.dp),
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "No Pages Added Yet",
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Choose how to add Page 1 of the handwritten answer sheet below:",
        fontFamily = FigtreeFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 17.5.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onTakePhoto,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = RoyalBlue,
            contentColor = Color.White,
          ),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("empty_take_photo_button"),
        ) {
          Icon(
            imageVector = Icons.Default.AddAPhoto,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Take Photo",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
          )
        }

        OutlinedButton(
          onClick = onChooseGallery,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.5f)),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LightBlueHighlight,
            contentColor = RoyalBlue,
          ),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("empty_choose_gallery_button"),
        ) {
          Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Choose from Gallery",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun ScannedPageCard(
  page: LocalScannedPage,
  isUploading: Boolean,
  onPreview: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("page_card_${page.pageNumber}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    border = BorderStroke(1.dp, BorderGray),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.75f)
          .background(LightBlueHighlight)
          .clickable(enabled = !isUploading, onClick = onPreview),
      ) {
        AsyncImage(
          model = page.uri,
          contentDescription = "Page ${page.pageNumber} preview",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )

        // Top gradient overlay for readability of badges
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color.Black.copy(alpha = 0.55f),
                  Color.Transparent,
                )
              )
            )
        )

        // Page number badge
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = RoyalBlue,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
        ) {
          Text(
            text = "Page ${page.pageNumber}",
            fontSize = 11.sp,
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }

        // Action buttons (Preview & Delete)
        if (!isUploading) {
          Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clickable(
                  role = Role.Button,
                  onClick = onPreview,
                ),
              contentAlignment = Alignment.TopEnd,
            ) {
              Box(
                modifier = Modifier
                  .padding(top = 6.dp, end = 3.dp)
                  .size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.White.copy(alpha = 0.94f))
                  .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.ZoomIn,
                  contentDescription = "Preview Page ${page.pageNumber}",
                  tint = RoyalBlue,
                  modifier = Modifier.size(14.dp),
                )
              }
            }

            Box(
              modifier = Modifier
                .size(48.dp)
                .clickable(
                  role = Role.Button,
                  onClick = onRemove,
                )
                .testTag("remove_page_${page.pageNumber}"),
              contentAlignment = Alignment.TopStart,
            ) {
              Box(
                modifier = Modifier
                  .padding(top = 6.dp, start = 3.dp)
                  .size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.White.copy(alpha = 0.94f))
                  .border(1.dp, BorderGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Remove Page ${page.pageNumber}",
                  tint = Color(0xFF64748B),
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }

      // Bottom strip - Display only compact green "Ready" status label
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = StatusSuccessContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = StatusSuccess,
              modifier = Modifier.size(11.dp),
            )
            Text(
              text = "Ready",
              fontSize = 11.sp,
              fontFamily = FigtreeFontFamily,
              fontWeight = FontWeight.Bold,
              color = StatusSuccess,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ScannedPagePreviewDialog(
  uri: Uri,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, BorderGray),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Page Preview",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp),
          ) {
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(LightBlueHighlight)
                .border(1.dp, BorderGray, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Preview",
                tint = TextSecondary,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .background(LightBlueHighlight)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
        ) {
          AsyncImage(
            model = uri,
            contentDescription = "Scanned page full preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

@Composable
private fun BottomScanActionBar(
  pageCount: Int,
  isUploading: Boolean,
  uploadProgressText: String?,
  onTakePhotoClick: () -> Unit,
  onChooseGalleryClick: () -> Unit,
  onSaveClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.White,
    shadowElevation = 6.dp,
    tonalElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onTakePhotoClick,
          enabled = !isUploading,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, BorderGray),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LightBlueHighlight,
            contentColor = RoyalBlue,
          ),
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("take_photo_button"),
        ) {
          Icon(
            imageVector = Icons.Default.AddAPhoto,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Camera",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
            maxLines = 1,
            softWrap = false,
          )
        }

        OutlinedButton(
          onClick = onChooseGalleryClick,
          enabled = !isUploading,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, BorderGray),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LightBlueHighlight,
            contentColor = RoyalBlue,
          ),
          contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("choose_gallery_button"),
        ) {
          Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Gallery",
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RoyalBlue,
            maxLines = 1,
            softWrap = false,
          )
        }

        Button(
          onClick = onSaveClick,
          enabled = !isUploading && pageCount > 0,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = RoyalBlue,
            contentColor = Color.White,
            disabledContainerColor = BorderGray,
            disabledContentColor = TextSecondary,
          ),
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
          modifier = Modifier
            .weight(1.25f)
            .height(44.dp)
            .testTag("save_paper_button"),
        ) {
          if (isUploading) {
            CircularProgressIndicator(
              modifier = Modifier.size(15.dp),
              strokeWidth = 2.dp,
              color = Color.White,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = uploadProgressText ?: "Saving...",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              softWrap = false,
            )
          } else {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Save ($pageCount)",
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

private fun createTempImageFileAndUri(context: Context): Pair<File, Uri> {
  val captureDir = File(context.cacheDir, "camera_captures").apply {
    if (!exists()) mkdirs()
  }
  val tempFile = File.createTempFile(
    "answer_page_${System.currentTimeMillis()}_",
    ".jpg",
    captureDir,
  )
  val authority = "${context.packageName}.fileprovider"
  val uri = FileProvider.getUriForFile(context, authority, tempFile)
  return tempFile to uri
}
