package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonItem(
  modifier: Modifier = Modifier,
  width: Dp = Dp.Unspecified,
  height: Dp = Dp.Unspecified,
  size: Dp = Dp.Unspecified,
  shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
  val transition = rememberInfiniteTransition(label = "skeleton")
  val translateAnim = transition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "skeleton_anim",
  )

  val shimmerColors = listOf(
    Color(0xFFE5E7EB),
    Color(0xFFF3F4F6),
    Color(0xFFE5E7EB),
  )

  val brush = Brush.linearGradient(
    colors = shimmerColors,
    start = Offset.Zero,
    end = Offset(x = translateAnim.value, y = translateAnim.value),
  )

  val sizeModifier = if (size != Dp.Unspecified) {
    Modifier.size(size)
  } else {
    Modifier
      .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
      .then(if (height != Dp.Unspecified) Modifier.height(height) else Modifier)
  }

  Box(
    modifier = modifier
      .then(sizeModifier)
      .background(brush, shape)
  )
}

@Composable
fun ExamCardSkeleton() {
  val cardShape = RoundedCornerShape(14.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .background(MaterialTheme.colorScheme.surface, cardShape)
      .border(1.dp, MaterialTheme.colorScheme.outline, cardShape)
      .padding(16.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        SkeletonItem(height = 18.dp, width = 140.dp)
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonItem(height = 13.dp, width = 90.dp)
      }
      Spacer(modifier = Modifier.width(8.dp))
      SkeletonItem(size = 32.dp, shape = RoundedCornerShape(8.dp))
    }
  }
}

@Composable
fun Modifier.skeletonSize(width: Dp, height: Dp): Modifier = this.width(width).height(height)

@Composable
fun SkeletonBox(
  modifier: Modifier = Modifier,
  shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
  SkeletonItem(modifier = modifier, shape = shape)
}
