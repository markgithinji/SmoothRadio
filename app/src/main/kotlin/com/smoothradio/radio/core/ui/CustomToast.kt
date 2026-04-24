package com.smoothradio.radio.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AppToast(
    toastType: ToastType,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val message: String
    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector

    when (toastType) {
        is ToastType.Error -> {
            message = toastType.message
            containerColor = colorScheme.errorContainer
            contentColor = colorScheme.onErrorContainer
            icon = Icons.Default.Warning
        }

        is ToastType.Success -> {
            message = toastType.message
            containerColor = colorScheme.primaryContainer
            contentColor = colorScheme.onPrimaryContainer
            icon = Icons.Default.CheckCircle
        }

        is ToastType.Warning -> {
            message = toastType.message
            containerColor = colorScheme.tertiaryContainer
            contentColor = colorScheme.onTertiaryContainer
            icon = Icons.Default.Info
        }

        is ToastType.Info -> {
            message = toastType.message
            containerColor = colorScheme.secondaryContainer
            contentColor = colorScheme.onSecondaryContainer
            icon = Icons.Default.Notifications
        }
    }

    // Fade in/out animation
    val animatedVisibility by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "toastVisibility"
    )

    // Slide up/down animation with bouncy spring
    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toastOffset"
    )

    // Auto dismiss after 2.5 seconds
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(2500)
            onDismiss()
        }
    }

    // Only render if visible
    if (animatedVisibility > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = animatedVisibility
                    translationY = animatedOffset
                }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon based on toast type
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )

                    // Message text
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )

                    // Dismiss button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

sealed class ToastType {
    data class Error(val message: String) : ToastType()
    data class Success(val message: String) : ToastType()
    data class Warning(val message: String) : ToastType()
    data class Info(val message: String) : ToastType()
}