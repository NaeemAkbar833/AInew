package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BorderGray
import com.example.ui.theme.BricolageGrotesqueFontFamily
import com.example.ui.theme.DarkRoyalBlue
import com.example.ui.theme.FigtreeFontFamily
import com.example.ui.theme.HeaderBlueGradient
import com.example.ui.theme.LightBlueHighlight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueGradient
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * GradeScan Primary Blue Gradient Button (#1F3FBF -> #4262E6).
 * Conforms to M3 touch targets (>= 48.dp) with bold Stitch typography and loading indicator support.
 */
@Composable
fun GradeScanPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  icon: ImageVector? = null,
  gradient: Brush = PrimaryBlueGradient,
  cornerRadius: Dp = 14.dp,
  testTag: String = "primary_button",
) {
  val shape = RoundedCornerShape(cornerRadius)

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .shadow(
        elevation = if (enabled) 4.dp else 0.dp,
        shape = shape,
        spotColor = DarkRoyalBlue.copy(alpha = 0.25f),
      )
      .clip(shape)
      .testTag(testTag)
      .clickable(
        enabled = enabled && !isLoading,
        onClick = onClick,
      ),
    shape = shape,
    color = Color.Transparent,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .background(
          if (enabled) gradient
          else Brush.horizontalGradient(
            listOf(
              Color(0xFF94A3B8).copy(alpha = 0.5f),
              Color(0xFFCBD5E1).copy(alpha = 0.5f),
            )
          )
        )
        .padding(horizontal = 20.dp, vertical = 14.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          modifier = Modifier.size(22.dp),
          color = Color.White,
          strokeWidth = 2.5.dp,
        )
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          if (icon != null) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text(
            text = text,
            color = Color.White,
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.1.sp,
          )
        }
      }
    }
  }
}

/**
 * GradeScan Outlined / Secondary Button with standard 48dp minimum touch target.
 */
@Composable
fun GradeScanSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  borderColor: Color = BorderGray,
  textColor: Color = PrimaryBlue,
  cornerRadius: Dp = 14.dp,
  testTag: String = "secondary_button",
) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .testTag(testTag),
    enabled = enabled,
    shape = RoundedCornerShape(cornerRadius),
    border = BorderStroke(1.dp, borderColor),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = Color.White,
      contentColor = textColor,
    ),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      fontFamily = FigtreeFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 15.sp,
    )
  }
}

/**
 * Stitch Curved Royal-Blue Gradient Header (#1F3FBF -> #2A4FD6) with 28.dp bottom corners.
 * Used as the signature top header across GradeScan screens.
 */
@Composable
fun GradeScanStitchHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  badgeText: String? = null,
  onBackClick: (() -> Unit)? = null,
  backTestTag: String = "back_button",
  actions: @Composable RowScope.() -> Unit = {},
  bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
  testTag: String = "top_app_bar",
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
      .background(HeaderBlueGradient)
      .statusBarsPadding()
      .padding(horizontal = 20.dp, vertical = 18.dp)
      .testTag(testTag),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          if (onBackClick != null) {
            IconButton(
              onClick = onBackClick,
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .testTag(backTestTag),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            if (!badgeText.isNullOrBlank()) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.padding(bottom = 6.dp),
              ) {
                Text(
                  text = badgeText.uppercase(),
                  color = Color.White,
                  fontFamily = FigtreeFontFamily,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.6.sp,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            Text(
              text = title,
              fontFamily = BricolageGrotesqueFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              letterSpacing = (-0.3).sp,
            )
            if (!subtitle.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = subtitle,
                fontFamily = FigtreeFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          content = actions,
        )
      }

      if (bottomContent != null) {
        Spacer(modifier = Modifier.height(14.dp))
        bottomContent()
      }
    }
  }
}

/**
 * GradeScan Hero Card featuring the blue gradient background.
 */
@Composable
fun GradeScanHeroCard(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  icon: ImageVector? = null,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
  testTag: String = "hero_card",
) {
  val shape = RoundedCornerShape(20.dp)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .shadow(elevation = 4.dp, shape = shape, spotColor = DarkRoyalBlue.copy(alpha = 0.18f))
      .clip(shape)
      .background(PrimaryBlueGradient)
      .padding(20.dp)
      .testTag(testTag),
  ) {
    Column {
      if (badgeText != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color.White.copy(alpha = 0.2f),
          modifier = Modifier.padding(bottom = 12.dp),
        ) {
          Text(
            text = badgeText,
            color = Color.White,
            fontFamily = FigtreeFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            color = Color.White,
            fontFamily = BricolageGrotesqueFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.88f),
            fontFamily = FigtreeFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
          )
        }

        if (icon != null) {
          Spacer(modifier = Modifier.width(12.dp))
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(52.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
              )
            }
          }
        }
      }

      if (actionText != null && onActionClick != null) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onActionClick,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = PrimaryBlue,
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.heightIn(min = 42.dp),
        ) {
          Text(
            text = actionText,
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
          )
        }
      }
    }
  }
}

/**
 * Standard Stitch GradeScan Card (#FFFFFF surface, #D1D9EC 1.dp border, 20.dp rounded corners, subtle shadow).
 */
@Composable
fun GradeScanCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 20.dp,
  onClick: (() -> Unit)? = null,
  testTag: String = "gradescan_card",
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, BorderGray, shape)
      .testTag(testTag)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = shape,
    colors = CardDefaults.cardColors(
      containerColor = Color.White,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      content = content,
    )
  }
}

/**
 * Badge status variants.
 */
enum class BadgeStatus {
  SUCCESS,
  WARNING,
  AMBER,
  INFO,
  NEUTRAL
}

/**
 * GradeScan Status Pill Badge.
 */
@Composable
fun GradeScanStatusBadge(
  text: String,
  status: BadgeStatus,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  testTag: String = "status_badge",
) {
  val (bgColor, fgColor) = when (status) {
    BadgeStatus.SUCCESS -> Pair(Color(0xFFDCFCE7), StatusSuccess)
    BadgeStatus.WARNING -> Pair(Color(0xFFFEE2E2), StatusWarning)
    BadgeStatus.AMBER -> Pair(Color(0xFFFEF3C7), StatusAmber)
    BadgeStatus.INFO -> Pair(LightBlueHighlight, DarkRoyalBlue)
    BadgeStatus.NEUTRAL -> Pair(
      Color(0xFFEEF2F9),
      TextSecondary,
    )
  }

  Surface(
    modifier = modifier.testTag(testTag),
    shape = RoundedCornerShape(8.dp),
    color = bgColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      val resolvedIcon = icon ?: when (status) {
        BadgeStatus.SUCCESS -> Icons.Default.CheckCircle
        BadgeStatus.WARNING -> Icons.Default.ErrorOutline
        BadgeStatus.AMBER -> Icons.Default.Warning
        BadgeStatus.INFO -> Icons.Default.Info
        BadgeStatus.NEUTRAL -> null
      }

      if (resolvedIcon != null) {
        Icon(
          imageVector = resolvedIcon,
          contentDescription = null,
          tint = fgColor,
          modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
      }

      Text(
        text = text,
        color = fgColor,
        fontFamily = FigtreeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
  }
}

/**
 * Standard Curved Royal-Blue Top App Bar for GradeScan screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScanTopAppBar(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBackClick: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  testTag: String = "top_app_bar",
) {
  GradeScanStitchHeader(
    title = title,
    subtitle = subtitle,
    onBackClick = onBackClick,
    actions = actions,
    modifier = modifier,
    testTag = testTag,
  )
}

/**
 * Section Header with title and optional action button.
 */
@Composable
fun GradeScanSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
  subtitle: String? = null,
  testTag: String = "section_header",
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontFamily = BricolageGrotesqueFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          color = TextSecondary,
        )
      }
    }

    if (actionText != null && onActionClick != null) {
      TextButton(
        onClick = onActionClick,
        modifier = Modifier.testTag("section_action_button"),
      ) {
        Text(
          text = actionText,
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = PrimaryBlue,
        )
      }
    }
  }
}

/**
 * Metric / Stat Card for dashboard and analytics views.
 */
@Composable
fun GradeScanStatCard(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  iconTint: Color = PrimaryBlue,
  changeText: String? = null,
  isPositiveChange: Boolean = true,
  testTag: String = "stat_card",
) {
  GradeScanCard(
    modifier = modifier.testTag(testTag),
    cornerRadius = 18.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        fontFamily = FigtreeFontFamily,
        fontSize = 13.sp,
        color = TextSecondary,
        fontWeight = FontWeight.Medium,
      )
      if (icon != null) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = iconTint.copy(alpha = 0.12f),
          modifier = Modifier.size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = value,
      fontFamily = BricolageGrotesqueFontFamily,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
    )

    if (changeText != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = changeText,
        fontFamily = FigtreeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (isPositiveChange) StatusSuccess else StatusWarning,
      )
    }
  }
}

/**
 * Empty state container inside a clean Stitch white card.
 */
@Composable
fun GradeScanEmptyState(
  icon: ImageVector,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
  testTag: String = "empty_state",
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag(testTag),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = CircleShape,
        color = LightBlueHighlight,
        modifier = Modifier.size(68.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(32.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = title,
        fontFamily = BricolageGrotesqueFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = message,
        fontFamily = FigtreeFontFamily,
        fontSize = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
      )

      if (actionText != null && onActionClick != null) {
        Spacer(modifier = Modifier.height(20.dp))
        GradeScanPrimaryButton(
          text = actionText,
          onClick = onActionClick,
          modifier = Modifier.widthIn(max = 240.dp),
        )
      }
    }
  }
}

/**
 * Error state container with retry option.
 */
@Composable
fun GradeScanErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Something went wrong",
  testTag: String = "error_state",
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, BorderGray),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag(testTag),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = CircleShape,
        color = Color(0xFFFEE2E2),
        modifier = Modifier.size(64.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = StatusWarning,
            modifier = Modifier.size(30.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = title,
        fontFamily = BricolageGrotesqueFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = message,
        fontFamily = FigtreeFontFamily,
        fontSize = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
      )

      Spacer(modifier = Modifier.height(20.dp))

      GradeScanSecondaryButton(
        text = "Try Again",
        onClick = onRetry,
        icon = Icons.Default.Refresh,
        modifier = Modifier.widthIn(max = 200.dp),
      )
    }
  }
}

/**
 * Clean centered Loading Indicator.
 */
@Composable
fun GradeScanLoadingIndicator(
  modifier: Modifier = Modifier,
  message: String? = null,
  testTag: String = "loading_indicator",
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(32.dp)
      .testTag(testTag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(
      color = PrimaryBlue,
      strokeWidth = 3.dp,
      modifier = Modifier.size(36.dp),
    )
    if (message != null) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = message,
        fontFamily = FigtreeFontFamily,
        fontSize = 14.sp,
        color = TextSecondary,
      )
    }
  }
}

@Composable
fun GradeScanConfirmationDialog(
  title: String,
  message: String,
  confirmText: String,
  dismissText: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector = Icons.Default.Warning,
  isDestructive: Boolean = true,
  isLoading: Boolean = false,
  dialogTestTag: String? = null,
  confirmTestTag: String? = null,
  dismissTestTag: String? = null,
) {
  val iconBgColor = if (isDestructive) Color(0xFFFEE2E2) else LightBlueHighlight
  val iconTintColor = if (isDestructive) StatusWarning else PrimaryBlue
  val confirmColor = if (isDestructive) StatusWarning else PrimaryBlue

  Dialog(
    onDismissRequest = { if (!isLoading) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      tonalElevation = 0.dp,
      shadowElevation = 8.dp,
      border = BorderStroke(1.dp, BorderGray),
      modifier = modifier
        .fillMaxWidth(0.90f)
        .widthIn(max = 400.dp)
        .let { if (dialogTestTag != null) it.testTag(dialogTestTag) else it },
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = 4.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconBgColor,
            modifier = Modifier.size(42.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTintColor,
                modifier = Modifier.size(20.dp),
              )
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = title,
              fontFamily = BricolageGrotesqueFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
              Text(
                text = subtitle,
                fontFamily = FigtreeFontFamily,
                fontSize = 12.sp,
                color = TextSecondary,
              )
            }
          }
        }

        HorizontalDivider(color = BorderGray, thickness = 1.dp)

        Text(
          text = message,
          fontFamily = FigtreeFontFamily,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = TextSecondary,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        )

        HorizontalDivider(color = BorderGray, thickness = 1.dp)

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            onClick = onDismiss,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, BorderGray),
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .let { if (dismissTestTag != null) it.testTag(dismissTestTag) else it },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = dismissText,
                fontFamily = FigtreeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (!isLoading) TextPrimary else TextSecondary,
              )
            }
          }

          Button(
            onClick = onConfirm,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = confirmColor,
              disabledContainerColor = BorderGray,
              contentColor = Color.White,
              disabledContentColor = TextSecondary,
            ),
            contentPadding = PaddingValues(),
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .let { if (confirmTestTag != null) it.testTag(confirmTestTag) else it },
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
              )
            } else {
              Text(
                text = confirmText,
                fontFamily = FigtreeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GradeScanProgressDialog(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
      usePlatformDefaultWidth = false,
    ),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color.White,
      tonalElevation = 0.dp,
      shadowElevation = 8.dp,
      border = BorderStroke(1.dp, BorderGray),
      modifier = modifier
        .fillMaxWidth(0.86f)
        .widthIn(max = 360.dp),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = LightBlueHighlight,
          modifier = Modifier.size(52.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              color = PrimaryBlue,
              strokeWidth = 2.5.dp,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = title,
          fontFamily = BricolageGrotesqueFontFamily,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = subtitle,
          fontFamily = FigtreeFontFamily,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
