package com.example.ui.paper.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Compact, content-sized ModalBottomSheet allowing the teacher to choose between taking a photo
 * with the camera or selecting an image from the device gallery.
 *
 * Harmonized with the GradeScan Royal Blue + White design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerBottomSheet(
  title: String = "Add Answer Sheet Page",
  subtitle: String = "Choose how you want to add this page",
  onDismiss: () -> Unit,
  onCameraClick: () -> Unit,
  onGalleryClick: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val configuration = LocalConfiguration.current
  val maxSheetHeight = (configuration.screenHeightDp * 0.78f).dp

  val headerGradient = Brush.linearGradient(
    colors = listOf(RoyalBlue, DarkRoyalBlue)
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = Color.White,
    scrimColor = Color.Black.copy(alpha = 0.45f),
    tonalElevation = 0.dp,
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    dragHandle = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White)
          .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .width(34.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(BorderGray)
        )
      }
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .background(Color.White)
        .navigationBarsPadding()
        .imePadding()
        .testTag("source_picker_bottom_sheet"),
    ) {
      // 1. Compact Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(headerGradient),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.DocumentScanner,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp),
            )
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = title,
              fontSize = 16.sp,
              lineHeight = 20.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              letterSpacing = (-0.2).sp,
            )
            Text(
              text = subtitle,
              fontSize = 11.5.sp,
              lineHeight = 15.5.sp,
              fontWeight = FontWeight.Normal,
              color = TextSecondary,
            )
          }
        }

        // Subtle Close Icon Button
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(LightBlueHighlight)
            .border(1.dp, BorderGray, CircleShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onDismiss,
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextSecondary,
            modifier = Modifier.size(15.dp),
          )
        }
      }

      HorizontalDivider(
        thickness = 1.dp,
        color = BorderGray,
      )

      // 2. Options Content
      Column(
        modifier = Modifier
          .weight(1f, fill = false)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SourceOptionCard(
          title = "Take Photo",
          description = "Capture the answer sheet using your camera",
          icon = Icons.Default.CameraAlt,
          onClick = onCameraClick,
          testTag = "source_camera_button",
        )

        SourceOptionCard(
          title = "Choose from Gallery",
          description = "Select one or more photos from your device",
          icon = Icons.Default.PhotoLibrary,
          onClick = onGalleryClick,
          testTag = "source_gallery_button",
        )
      }

      // 3. Footer Cancel Button
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White),
      ) {
        HorizontalDivider(
          thickness = 1.dp,
          color = BorderGray,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, BorderGray),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = Color.White,
              contentColor = TextPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("source_cancel_button"),
          ) {
            Text(
              text = "Cancel",
              fontSize = 13.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SourceOptionCard(
  title: String,
  description: String,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
    shape = RoundedCornerShape(12.dp),
    color = Color.White,
    border = BorderStroke(1.dp, BorderGray),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(LightBlueHighlight)
          .border(
            width = 1.dp,
            color = RoyalBlue.copy(alpha = 0.2f),
            shape = RoundedCornerShape(10.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(18.dp),
        )
      }

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = title,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary,
        )
        Text(
          text = description,
          fontSize = 11.5.sp,
          lineHeight = 15.5.sp,
          fontWeight = FontWeight.Normal,
          color = TextSecondary,
        )
      }

      Box(
        modifier = Modifier
          .size(26.dp)
          .clip(CircleShape)
          .background(LightBlueHighlight),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = RoyalBlue,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
