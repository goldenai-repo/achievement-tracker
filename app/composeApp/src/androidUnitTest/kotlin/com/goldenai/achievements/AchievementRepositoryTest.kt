package com.goldenai.achievements

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.goldenai.achievements.core.AppResult
import com.goldenai.achievements.core.model.Achievement
import com.goldenai.achievements.core.model.AchievementType
import com.goldenai.achievements.db.AchievementDatabase
import com.goldenai.achievements.features.achievements.data.AchievementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementRepositoryTest {

    private fun repository(): AchievementRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AchievementDatabase.Schema.create(driver)
        return AchievementRepository(AchievementDatabase(driver))
    }

    @Test
    fun `guest create stores id type timestamp location content and null owner`() {
        runBlocking {
            val repo = repository()
            val result = repo.create(
                type = "geography.country",
                content = "  Japan  ",
                timestamp = 1_700_000_000_000,
                locationName = "Tokyo",
            )
            val created = (result as AppResult.Ok).value

            assertTrue(created.id.isNotBlank())
            assertEquals("geography.country", created.type)
            assertEquals("Geography", created.category)
            assertEquals(1_700_000_000_000, created.timestamp)
            assertEquals("Tokyo", created.locationName)
            assertEquals("Japan", created.content)
            assertNull(created.ownerUid)

            val loaded = repo.get(created.id)
            assertNotNull(loaded)
            assertEquals(created.id, loaded.id)
            assertEquals("geography.country", loaded.type)
            assertEquals("Geography", loaded.category)
            assertEquals(1_700_000_000_000, loaded.timestamp)
            assertEquals("Tokyo", loaded.locationName)
            assertEquals("Japan", loaded.content)
            assertNull(loaded.ownerUid)
        }
    }

    @Test
    fun `create persists gps coordinates`() {
        runBlocking {
            val repo = repository()
            val created = unwrap(
                repo.create(
                    type = "geography.city",
                    content = "Kyoto",
                    timestamp = 1L,
                    latitude = 35.0116,
                    longitude = 135.7681,
                    locationName = "Kyoto",
                ),
            )
            val loaded = repo.get(created.id)
            assertNotNull(loaded)
            assertEquals(35.0116, loaded.latitude)
            assertEquals(135.7681, loaded.longitude)
            assertEquals("Kyoto", loaded.locationName)
        }
    }

    @Test
    fun `create persists mediaUrl`() {
        runBlocking {
            val repo = repository()
            val created = unwrap(
                repo.create(
                    type = "wildlife.animal",
                    content = "Red fox",
                    timestamp = 1L,
                    mediaUrl = "https://example.com/fox.jpg",
                ),
            )
            assertEquals("https://example.com/fox.jpg", repo.get(created.id)?.mediaUrl)
        }
    }

    @Test
    fun `blank mediaUrl is stored as null`() {
        runBlocking {
            val repo = repository()
            val created = unwrap(
                repo.create(
                    type = "wildlife.animal",
                    content = "Red fox",
                    timestamp = 1L,
                    mediaUrl = "  ",
                ),
            )
            assertNull(repo.get(created.id)?.mediaUrl)
        }
    }

    @Test
    fun `create persists optional owner uid without hiding guest rows`() {
        runBlocking {
            val repo = repository()
            val guest = unwrap(
                repo.create(type = "culture.museum", content = "Louvre", timestamp = 1L),
            )
            val owned = unwrap(
                repo.create(
                    type = "heritage.unesco",
                    content = "Nara",
                    timestamp = 2L,
                    ownerUid = "uid-abc",
                ),
            )

            assertNull(repo.get(guest.id)?.ownerUid)
            assertEquals("uid-abc", repo.get(owned.id)?.ownerUid)
            assertNotNull(repo.get(guest.id))
            assertNotNull(repo.get(owned.id))
        }
    }

    @Test
    fun `update preserves owner uid and coordinates`() {
        runBlocking {
            val repo = repository()
            val created = unwrap(
                repo.create(
                    type = "entertainment.movie",
                    content = "Old title",
                    timestamp = 1L,
                    latitude = 1.0,
                    longitude = 2.0,
                    ownerUid = "uid-abc",
                ),
            )
            val updated = unwrap(repo.update(created.copy(content = "New title")))
            val loaded = repo.get(updated.id)
            assertNotNull(loaded)
            assertEquals("New title", loaded.content)
            assertEquals("uid-abc", loaded.ownerUid)
            assertEquals(1.0, loaded.latitude)
            assertEquals(2.0, loaded.longitude)
        }
    }

    @Test
    fun `watchByTypes returns only achievements in the selected category`() {
        runBlocking {
            val repo = repository()
            unwrap(repo.create(type = "geography.country", content = "Japan", timestamp = 3L))
            unwrap(repo.create(type = "geography.city", content = "Kyoto", timestamp = 2L))
            unwrap(repo.create(type = "wildlife.animal", content = "Red fox", timestamp = 1L))

            val items = repo.watchByTypes(AchievementType.keysForCategory("Geography")).first()
            assertEquals(listOf("Japan", "Kyoto"), items.map { it.content })
        }
    }

    @Test
    fun `create with type content date and place appears in that category list`() {
        runBlocking {
            val repo = repository()
            unwrap(
                repo.create(
                    type = "wildlife.animal",
                    content = "Red fox",
                    timestamp = 1_700_000_000_000,
                    locationName = "Hokkaido",
                ),
            )
            val items = repo.watchByTypes(AchievementType.keysForCategory("Wildlife")).first()
            assertEquals(1, items.size)
            val saved = items.single()
            assertEquals("wildlife.animal", saved.type)
            assertEquals("Wildlife", saved.category)
            assertEquals("Red fox", saved.content)
            assertEquals(1_700_000_000_000, saved.timestamp)
            assertEquals("Hokkaido", saved.locationName)
        }
    }

    private fun unwrap(result: AppResult<Achievement>): Achievement {
        assertIs<AppResult.Ok<Achievement>>(result)
        return result.value
    }
}
