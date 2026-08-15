package com.goldenai.achievements.core

import com.goldenai.achievements.BuildConfig
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun nowEpochMillis(): Long = System.currentTimeMillis()

actual fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

actual fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

actual fun formatIsoUtc(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

actual fun parseIsoUtc(value: String): Long = runCatching {
    Instant.parse(value).toEpochMilli()
}.getOrElse {
    runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrElse {
        // SQLite may return a naive UTC datetime without a zone suffix.
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

actual val mapStyleUrl: String = BuildConfig.MAP_STYLE_URL
