package com.goldenai.achievements.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Renders a Unicode emoji string.
 *
 * On iOS, Compose/Skia defaults to `.AppleSystemUIFont`, which has no color-emoji
 * glyphs. [platformEmojiFontFamily] pins Apple Color Emoji so regional-indicator
 * flags and U+FE0F sequences (🗺️, 🏙️, 🏛️) do not render as □.
 * Android returns null and keeps the platform cascade (Noto Color Emoji).
 */
@Composable
fun EmojiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val emojiFamily = platformEmojiFontFamily()
    Text(
        text = text,
        modifier = modifier,
        style = if (emojiFamily != null) style.copy(fontFamily = emojiFamily) else style,
    )
}

/** Null on Android. Non-null Apple Color Emoji family on iOS. */
expect fun platformEmojiFontFamily(): FontFamily?

/**
 * Registers the platform emoji face as a Skia fallback for mixed emoji+Latin text
 * (e.g. filter chips). No-op on Android.
 */
@Composable
expect fun EnsurePlatformEmojiFontFallback()
