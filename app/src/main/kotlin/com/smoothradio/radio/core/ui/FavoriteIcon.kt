package com.smoothradio.radio.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smoothradio.radio.R

@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    buttonSize: Dp = 32.dp
) {
    AnimatedContent(
        targetState = isFavorite,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(tween(200))) togetherWith
                    (scaleOut(
                        targetScale = 0.5f,
                        animationSpec = tween(150)
                    ) + fadeOut(tween(150)))
        },
        label = "favoriteIcon"
    ) { isFav ->
        IconButton(
            onClick = onFavoriteClick,
            modifier = modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(id = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                tint = if (isFav) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
