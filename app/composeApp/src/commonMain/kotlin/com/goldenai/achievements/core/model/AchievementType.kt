package com.goldenai.achievements.core.model

/**
 * Registry of the achievement types supported by the product. The [key] is the
 * canonical value stored in the local database and exchanged with the API.
 */
enum class AchievementType(
    val key: String,
    val category: String,
    val label: String,
    val emoji: String,
    val visibleInMvp: Boolean = true,
) {
    GeographyCountry("geography.country", "Country", "Country visited", "🌍"),
    GeographyState("geography.state", "Province / State", "Province or state visited", "🗺️"),
    // Kept for data compatibility, but city check-ins are not implemented in
    // the current catalog/check-in flow and should not appear as a filter.
    GeographyCity("geography.city", "City", "City visited", "🏙️", visibleInMvp = false),
    WildlifeAnimal("wildlife.animal", "Wildlife", "Animal species observed", "🦁"),
    WildlifePlant("wildlife.plant", "Wildlife", "Wild plant species observed", "🌿"),
    CultureMuseum("culture.museum", "Culture", "Museum or gallery visited", "🏛️"),
    EntertainmentMovie("entertainment.movie", "Entertainment", "Movie watched", "🎬"),
    CulinaryMichelin("culinary.michelin", "Culinary", "Michelin-starred restaurant visited", "⭐"),
    HeritageUnesco("heritage.unesco", "UNESCO Heritage", "UNESCO World Heritage Site visited", "🏯");

    companion object {
        fun fromKey(key: String): AchievementType? = entries.firstOrNull { it.key == key }
    }
}
