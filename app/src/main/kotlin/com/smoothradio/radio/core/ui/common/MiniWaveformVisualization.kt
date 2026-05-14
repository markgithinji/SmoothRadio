package com.smoothradio.radio.core.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MiniWaveformVisualization(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    barCount: Int = 8,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "miniWaveform")

    Row(
        modifier = modifier.height(height).testTag("mini_waveform"),
        horizontalArrangement = Arrangement.spacedBy(barSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val delay = (index * 60)
            val amplitude by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween<Float>(400, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(amplitude)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = 0.7f))
            )
        }
    }
}
