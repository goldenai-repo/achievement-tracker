package com.goldenai.achievements.core

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun randomUuid(): String = NSUUID().UUIDString

actual fun nowEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

private fun formatter(dateStyle: ULong, timeStyle: ULong): NSDateFormatter =
    NSDateFormatter().apply {
        this.dateStyle = dateStyle
        this.timeStyle = timeStyle
    }

actual fun formatDate(epochMillis: Long): String =
    formatter(NSDateFormatterMediumStyle, NSDateFormatterNoStyle)
        .stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

actual fun formatDateTime(epochMillis: Long): String =
    formatter(NSDateFormatterMediumStyle, NSDateFormatterShortStyle)
        .stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

actual fun formatIsoUtc(epochMillis: Long): String = formatUtcIso(epochMillis)

actual fun parseIsoUtc(value: String): Long = parseUtcIso(value)

actual val mapStyleUrl: String = "https://tiles.openfreemap.org/styles/bright"

/**
 * Pure-Kotlin UTC helpers avoid brittle NSTimeZone interop on Kotlin/Native.
 * Parsing mirrors Android's Instant/OffsetDateTime/LocalDateTime fallbacks.
 */
internal fun formatUtcIso(epochMillis: Long): String {
    val parts = utcPartsFromEpochMillis(epochMillis)
    return buildString {
        append(parts.year.pad(4))
        append('-')
        append(parts.month.pad(2))
        append('-')
        append(parts.day.pad(2))
        append('T')
        append(parts.hour.pad(2))
        append(':')
        append(parts.minute.pad(2))
        append(':')
        append(parts.second.pad(2))
        append('.')
        append(parts.millis.pad(3))
        append('Z')
    }
}

internal fun parseUtcIso(value: String): Long {
    val trimmed = value.trim()
    val match = UTC_PATTERN.matchEntire(trimmed)
        ?: error("Invalid ISO date: $value")
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    val hour = match.groupValues[4].toInt()
    val minute = match.groupValues[5].toInt()
    val second = match.groupValues[6].toInt()
    val fraction = match.groupValues[7]
    val offset = match.groupValues[8]
    val millis = when {
        fraction.isEmpty() -> 0
        // Skip leading '.' from the capture group.
        fraction.length >= 4 -> fraction.drop(1).take(3).toInt()
        else -> fraction.drop(1).padEnd(3, '0').toInt()
    }
    var totalSeconds = daysSinceEpoch(year, month, day) * 86_400L
    totalSeconds += hour * 3_600L
    totalSeconds += minute * 60L
    totalSeconds += second
    totalSeconds -= parseOffsetSeconds(offset)
    return totalSeconds * 1_000L + millis
}

/**
 * Accepts the same families Android Instant/OffsetDateTime/LocalDateTime accept:
 * with/without fractional seconds, Z or +00:00 / -05:00 offsets, or naive UTC.
 */
private val UTC_PATTERN =
    Regex(
        """(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(\.\d+)?""" +
            """(?:Z|z|([+-]\d{2}:?\d{2}))?""",
    )

/** Converts "+05:30" / "+0530" / "-08:00" into seconds east of UTC. */
private fun parseOffsetSeconds(offset: String): Long {
    if (offset.isEmpty()) return 0L
    val sign = if (offset[0] == '-') -1 else 1
    val digits = offset.drop(1).replace(":", "")
    require(digits.length == 4) { "Invalid timezone offset: $offset" }
    val hours = digits.substring(0, 2).toInt()
    val minutes = digits.substring(2, 4).toInt()
    return sign * (hours * 3_600L + minutes * 60L)
}

private data class UtcParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millis: Int,
)

private fun utcPartsFromEpochMillis(epochMillis: Long): UtcParts {
    var rem = epochMillis
    // Normalize negative remainders so pre-epoch values stay correct.
    var millis = (rem % 1_000).toInt()
    rem /= 1_000
    if (millis < 0) {
        millis += 1_000
        rem -= 1
    }
    var second = (rem % 60).toInt()
    rem /= 60
    if (second < 0) {
        second += 60
        rem -= 1
    }
    var minute = (rem % 60).toInt()
    rem /= 60
    if (minute < 0) {
        minute += 60
        rem -= 1
    }
    var hour = (rem % 24).toInt()
    rem /= 24
    if (hour < 0) {
        hour += 24
        rem -= 1
    }
    var days = rem.toInt()

    var year = 1970
    if (days >= 0) {
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366 else 365
            if (days < daysInYear) break
            days -= daysInYear
            year++
        }
    } else {
        while (days < 0) {
            year--
            days += if (isLeapYear(year)) 366 else 365
        }
    }

    val monthLengths = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    for (length in monthLengths) {
        if (days < length) break
        days -= length
        month++
    }

    return UtcParts(year, month, days + 1, hour, minute, second, millis)
}

private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
    var days = 0L
    for (y in 1970 until year) {
        days += if (isLeapYear(y)) 366 else 365
    }
    if (year < 1970) {
        for (y in year until 1970) {
            days -= if (isLeapYear(y)) 366 else 365
        }
    }
    val monthLengths = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (index in 0 until month - 1) {
        days += monthLengths[index]
    }
    days += day - 1
    return days
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun Int.pad(width: Int): String = toString().padStart(width, '0')
