package com.smoothradio.radio.core.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.smoothradio.radio.R

/**
 * Remembers a safe resource ID for a radio station logo.
 * Falls back to a default icon if the given ID is invalid or not a drawable.
 */
@Composable
fun rememberSafeLogoId(logoResource: Int): Int {
    val context = LocalContext.current
    return remember(logoResource) {
        try {
            if (logoResource != 0 && context.resources.getResourceTypeName(logoResource) == "drawable") {
                logoResource
            } else {
                R.drawable.ic_radio_default
            }
        } catch (e: Exception) {
            R.drawable.ic_radio_default
        }
    }
}
