package com.smoothradio.radio.feature.player

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.smoothradio.radio.R

@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    AndroidView(
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
            }
        },
        modifier = modifier,
        update = { button ->
            val castDrawable = ContextCompat.getDrawable(button.context, R.drawable.ic_player_cast)
            castDrawable?.let {
                val wrapped = DrawableCompat.wrap(it).mutate()
                color?.let { tintColor ->
                    DrawableCompat.setTint(wrapped, tintColor.toArgb())
                }
                button.setRemoteIndicatorDrawable(wrapped)
            }
        }
    )
}
