package com.smoothradio.radio.core.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.smoothradio.radio.core.util.AdConfig

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    alpha: Float = 0.5f
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .wrapContentWidth()
                .height(height),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = alpha)
            )
        ) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        adUnitId = AdConfig.bannerAdId
                        setAdSize(AdSize.BANNER)
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}
