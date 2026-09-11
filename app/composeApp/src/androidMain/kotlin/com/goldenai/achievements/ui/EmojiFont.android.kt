package com.goldenai.achievements.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

actual fun platformEmojiFontFamily(): FontFamily? = null

@Composable
actual fun EnsurePlatformEmojiFontFallback() = Unit
