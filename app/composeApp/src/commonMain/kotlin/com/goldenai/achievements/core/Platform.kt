package com.goldenai.achievements.core

expect fun randomUuid(): String

expect fun nowEpochMillis(): Long

/** Formats an epoch-millis instant as a locale-aware date, e.g. "Jul 30, 2026". */
expect fun formatDate(epochMillis: Long): String

/** Formats an epoch-millis instant as a locale-aware date and time. */
expect fun formatDateTime(epochMillis: Long): String
