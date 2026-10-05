package com.example.ui.paper

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.LocalScannedPage
import com.example.ui.paper.components.SourcePickerBottomSheet
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPaperScreen(
  studentId: String,
  classId: String,
  viewModel: ScanPaperViewModel,
  onNavigateBack: () -> Unit,
  onScanFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  var showSourcePicker by remember { mutableStateOf(false) }
  var showDiscardDialog by remember { mutableStateOf(false) }
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  // Camera capture launcher
  val takePictureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    if (success) {
      tempCameraUri?.let { uri ->
        viewModel.addPage(uri)
      }
    }
    tempCameraUri = null
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      try {
        val tempFile = File.createTempFile("scan_page_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          tempFile,
        )
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
      } catch (e: Exception) {
        Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(context, "Camera permission is required to capture pages.", Toast.LENGTH_LONG).show()
    }
  }

  // Photo gallery picker launcher (multiple pages supported)
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris ->
    if (uris.isNotEmpty()) {
      viewModel.addPages(uris)
    }
  }

  fun launchCamera() {
    val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
      try {
        val tempFile = File.createTempFile("scan_page_", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          tempFile,
        )
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
      } catch (e: Exception) {
        Toast.makeText(context, "Could not launch camera", Toast.LENGTH_SHORT).show()
      }
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  fun launchGallery() {
    galleryLauncher.launch(
      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
  }

  // Intercept back navigation if pages are pending
  BackHandler {
    if (uiState.pages.isNotEmpty() && !uiState.isUploading) {
      showDiscardDialog = true
    } else if (!uiState.isUploading) {
      viewModel.clearAllPages()
      onNavigateBack()
    }
  }

  if (showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { showDiscardDialog = false },
      title = { Text("Discard scanned pages?") },
      text = { Text("You have ${uiState.pages.size} scanned page(s) that have not been uploaded. If you leave now, they will be lost.") },
      confirmButton = {
        Button(
          onClick = {
            showDiscardDialog = false
            viewModel.clearAllPages()
            onNavigateBack()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_discard_scan_button"),
        ) {
          Text("Discard")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDiscardDialog = false },
          modifier = Modifier.testTag("cancel_discard_scan_button"),
        ) {
          Text("Keep Scanning")
        }
      },
      shape = RoundedCornerShape(16.dp),
    )
  }

  if (showSourcePicker) {
    SourcePickerBottomSheet(
      onDismiss = { showSourcePicker = false },
      onCameraSelect = { launchCamera() },
      onGallerySelect = { launchGallery() },
    )
  }

  // Full-screen page preview dialog
  val previewUri = uiState.previewPageUri
  if (previewUri != null) {
    Dialog(
      onDismissRequest = { viewModel.setPreviewPageUri(null) },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .padding(16.dp),
      ) {
        AsyncImage(
          model = previewUri,
          contentDescription = "Full page preview",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )

        IconButton(
          onClick = { viewModel.setPreviewPageUri(null) },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close preview",
            tint = Color.White,
          )
        }
      }
    }
  }

  // Upload Progress Dialog
  if (uiState.isUploading) {
    Dialog(
      onDismissRequest = {}, // Non-dismissible while uploading
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(0.9f),
      ) {
        Column(
          modifier = Modifier.padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(44.dp),
          )
          Spacer(modifier = Modifier.height(20.dp))
          Text(
            text = "Saving Paper Submission",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(8.dp))
          val progress = uiState.uploadProgress
          val progressText = if (progress != null) {
            "Uploading page ${progress.first} of ${progress.second}..."
          } else {
            "Preparing files..."
          }
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Scan Paper",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (uiState.pages.isNotEmpty() && !uiState.isUploading) {
                showDiscardDialog = true
              } else if (!uiState.isUploading) {
                viewModel.clearAllPages()
                onNavigateBack()
              }
            },
            modifier = Modifier.testTag("scan_paper_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalButton(
            onClick = { showSourcePicker = true },
            enabled = !uiState.isUploading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("add_page_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Page")
          }

          Button(
            onClick = {
              viewModel.finishScan(
                context = context,
                studentId = studentId,
                classId = classId,
                onSuccess = onScanFinished,
              )
            },
            enabled = uiState.pages.isNotEmpty() && !uiState.isUploading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
              .weight(1.3f)
              .testTag("finish_scan_button"),
          ) {
            val count = uiState.pages.size
            Text(if (count > 0) "Finish Scan ($count)" else "Finish Scan")
          }
        }
      }
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    modifier = modifier.testTag("scan_paper_screen"),
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(paddingValues),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      ) {
        // Upload Error Banner
        val error = uiState.uploadError
        if (error != null) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .testTag("upload_error_card"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = viewModel::clearError,
                modifier = Modifier.size(24.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Dismiss error",
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          }
        }

        if (uiState.pages.isEmpty()) {
          // Empty State Prompt
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center,
          ) {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.fillMaxWidth(0.9f),
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier.size(56.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.CameraAlt,
                      contentDescription = "Camera",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(28.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "No pages scanned yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                  text = "Capture or select handwritten answer sheet pages to assemble this student's paper.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilledTonalButton(
                    onClick = { launchCamera() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("empty_state_camera_button"),
                  ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera")
                  }

                  FilledTonalButton(
                    onClick = { launchGallery() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("empty_state_gallery_button"),
                  ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery")
                  }
                }
              }
            }
          }
        } else {
          // Review Grid of Scanned Pages
          Text(
            text = "Scanned Pages (${uiState.pages.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp),
          )

          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
              .fillMaxSize()
              .testTag("scanned_pages_grid"),
          ) {
            items(uiState.pages, key = { it.id }) { page ->
              ScannedPageCard(
                page = page,
                onDelete = { viewModel.removePage(page.id) },
                onPreview = { viewModel.setPreviewPageUri(page.uri) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ScannedPageCard(
  page: LocalScannedPage,
  onDelete: () -> Unit,
  onPreview: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("scanned_page_card_${page.pageNumber}"),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(180.dp)
          .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
          .clickable(onClick = onPreview),
      ) {
        AsyncImage(
          model = page.uri,
          contentDescription = "Page ${page.pageNumber}",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )

        // Page number badge
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
        ) {
          Text(
            text = "Page ${page.pageNumber}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }

        // Preview overlay button
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.5f),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(32.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.ZoomIn,
              contentDescription = "Preview",
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = "Page ${page.pageNumber}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(32.dp)
            .testTag("delete_page_${page.pageNumber}_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Remove page",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
