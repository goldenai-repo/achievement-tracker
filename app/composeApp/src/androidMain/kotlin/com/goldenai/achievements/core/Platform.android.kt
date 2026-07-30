package com.goldenai.achievements.core

import java.text.DateFormat
import java.util.Date
import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun nowEpochMillis(): Long = System.currentTimeMillis()

actual fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

actual fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
