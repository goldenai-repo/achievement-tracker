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
    HeritageUnesco("heritage.unesco", "Heritage", "UNESCO World Heritage Site visited", "🏯");

    companion object {
        fun fromKey(key: String): AchievementType? = entries.firstOrNull { it.key == key }

        /** Product categories in registry order: Geography, Wildlife, Culture, Entertainment, Culinary, Heritage. */
        val categories: List<AchievementCategory> =
            entries.groupBy { it.category }.map { (name, types) ->
                AchievementCategory(
                    name = name,
                    emoji = types.first().emoji,
                    typeKeys = types.map { it.key },
                )
            }

        fun typesForCategory(category: String): List<AchievementType> =
            entries.filter { it.category == category }

        fun keysForCategory(category: String): List<String> =
            typesForCategory(category).map { it.key }

        /** List filter to open after saving a record of this type. */
        fun listCategoryFor(typeKey: String): String? = fromKey(typeKey)?.category
    }
}

/** Group of [AchievementType]s that share a [name] (e.g. Geography). */
data class AchievementCategory(
    val name: String,
    val emoji: String,
    val typeKeys: List<String>,
)
