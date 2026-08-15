package com.goldenai.achievements.core

expect fun randomUuid(): String

expect fun nowEpochMillis(): Long

/** Formats an epoch-millis instant as a locale-aware date, e.g. "Jul 30, 2026". */
expect fun formatDate(epochMillis: Long): String

/** Formats an epoch-millis instant as a locale-aware date and time. */
expect fun formatDateTime(epochMillis: Long): String

/** ISO-8601 UTC value accepted by the FastAPI datetime field. */
expect fun formatIsoUtc(epochMillis: Long): String

/** Parses the ISO-8601 values returned by FastAPI. */
expect fun parseIsoUtc(value: String): Long

/** Configured MapLibre style URL for the current platform/build. */
expect val mapStyleUrl: String
