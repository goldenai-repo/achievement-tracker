package com.goldenai.achievements.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont

/**
 * Pin Skia text to the system color-emoji face for emoji-only [EmojiText].
 *
 * Compose Multiplatform's iOS default aliases (`.AppleSystemUIFont`, Helvetica) do not
 * contain emoji glyphs. Without this, category emoji that include U+FE0F (🗺️, 🏙️, 🏛️)
 * and regional-indicator flag pairs from [countryFlagEmoji] render as empty squares.
 */
@OptIn(ExperimentalTextApi::class)
actual fun platformEmojiFontFamily(): FontFamily? =
    FontFamily(SystemFont("Apple Color Emoji"))

/**
 * Also register Apple Color Emoji as a last-resort Skia fallback so mixed strings
 * (emoji + Latin labels) resolve emoji code points without changing the body font.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
actual fun EnsurePlatformEmojiFontFallback() {
    val resolver = LocalFontFamilyResolver.current
    val emojiFamily = platformEmojiFontFamily() ?: return
    LaunchedEffect(resolver, emojiFamily) {
        resolver.preload(emojiFamily)
    }
}
