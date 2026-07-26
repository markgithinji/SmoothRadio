package com.smoothradio.radio.feature.info.domain.usecase

import com.smoothradio.radio.feature.info.domain.model.ChangelogItem
import javax.inject.Inject

class GetChangelogItemsUseCase @Inject constructor() {
    operator fun invoke(): List<ChangelogItem> {
        return listOf(
            ChangelogItem(
                "Modern UI Redesign",
                "A fresh look and feel with Material 3 styling and updated icons."
            ),
            ChangelogItem(
                "Interactive Seek Bar",
                "Take full control of your listening with the new interactive seek bar."
            ),
            ChangelogItem(
                "Faster Playback",
                "Start listening instantly with optimized stream loading."
            ),
            ChangelogItem(
                "Fewer Interruptions",
                "We've reduced ad frequency for a smoother listening experience."
            ),
            ChangelogItem(
                "Dark & Light Modes",
                "Beautifully designed for both environments, supporting your system's theme perfectly."
            ),
            ChangelogItem(
                "Adaptive Design",
                "Seamlessly optimized for all screen sizes, from compact phones to large tablets."
            ),
            ChangelogItem(
                "Built-in Equalizer",
                "Fine-tune your audio with the new integrated equalizer."
            ),
            ChangelogItem(
                "Bluetooth Headset Support",
                "Full support for Bluetooth headset media buttons and seamless playback control."
            ),
            ChangelogItem(
                "Google Cast Support",
                "Easily cast your favorite stations to your TV or speakers."
            ),
            ChangelogItem(
                "Grid & List Views",
                "Choose how you browse with customizable station layouts."
            ),
            ChangelogItem("Smarter Search", "Find your favorite stations faster than ever before.")
        )
    }
}
