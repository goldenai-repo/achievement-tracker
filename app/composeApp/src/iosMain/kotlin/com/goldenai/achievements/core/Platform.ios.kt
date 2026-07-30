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
