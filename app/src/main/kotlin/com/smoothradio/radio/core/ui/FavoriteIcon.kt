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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    buttonSize: Dp = 32.dp
) {
    AnimatedContent(
        targetState = isFavorite,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(tween(200))) togetherWith
                    (scaleOut(
                        targetScale = 0.3f,
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
                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}