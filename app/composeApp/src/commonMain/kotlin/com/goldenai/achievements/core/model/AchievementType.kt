package com.goldenai.achievements.core.model

/**
 * Registry of the achievement types supported by the product. The [key] is the
 * canonical value stored in the local database and in Firestore documents.
 */
enum class AchievementType(
    val key: String,
    val category: String,
    val label: String,
    val emoji: String,
) {
    GeographyCountry("geography.country", "Geography", "Country visited", "🌍"),
    GeographyState("geography.state", "Geography", "State or province visited", "🗺️"),
    GeographyCity("geography.city", "Geography", "City visited", "🏙️"),
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
