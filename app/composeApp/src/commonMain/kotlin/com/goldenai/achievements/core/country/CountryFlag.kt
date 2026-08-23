package com.goldenai.achievements.core.country

/**
 * Converts an ISO 3166-1 alpha-2 country code to its flag emoji.
 *
 * Country codes come from the GeoNames catalog and are normally already
 * normalized to two uppercase letters. Invalid or missing values return a
 * neutral globe fallback instead of producing an unrelated symbol.
 */
fun countryFlagEmoji(countryCode: String?): String {
    val code = countryCode?.trim()?.uppercase()
    if (code == null || code.length != 2 || code.any { it !in 'A'..'Z' }) {
        return "🌍"
    }

    // Regional indicator symbols are Unicode code points U+1F1E6..U+1F1FF.
    return regionalIndicator(code[0]) + regionalIndicator(code[1])
}

private fun regionalIndicator(letter: Char): String {
    val codePoint = 0x1F1E6 + (letter.code - 'A'.code)
    val high = ((codePoint - 0x10000) ushr 10) + 0xD800
    val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
    return charArrayOf(high.toChar(), low.toChar()).concatToString()
}
