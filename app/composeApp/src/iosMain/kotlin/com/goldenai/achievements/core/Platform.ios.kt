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

/** Pure-Kotlin UTC helpers avoid brittle NSTimeZone interop on Kotlin/Native. */
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
    val match = UTC_PATTERN.matchEntire(value.trim())
        ?: error("Invalid ISO date: $value")
    val (year, month, day, hour, minute, second, millis) = match.destructured
    var totalSeconds = daysSinceEpoch(year.toInt(), month.toInt(), day.toInt()) * 86_400L
    totalSeconds += hour.toInt() * 3_600L
    totalSeconds += minute.toInt() * 60L
    totalSeconds += second.toInt()
    return totalSeconds * 1_000L + millis.toInt()
}

private val UTC_PATTERN =
    Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{3})Z""")

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
    val millis = (rem % 1_000).toInt().also { rem /= 1_000 }
    val second = (rem % 60).toInt().also { rem /= 60 }
    val minute = (rem % 60).toInt().also { rem /= 60 }
    val hour = (rem % 24).toInt().also { rem /= 24 }
    var days = rem.toInt()

    var year = 1970
    while (true) {
        val daysInYear = if (isLeapYear(year)) 366 else 365
        if (days < daysInYear) break
        days -= daysInYear
        year++
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
