package com.goldenai.achievements

import com.goldenai.achievements.features.sync.SyncLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncLogicTest {

    @Test
    fun `remote wins when there is no local row`() {
        assertTrue(SyncLogic.shouldApplyRemote(localUpdatedAt = null, remoteUpdatedAt = 1000))
    }

    @Test
    fun `remote wins when strictly newer`() {
        assertTrue(SyncLogic.shouldApplyRemote(localUpdatedAt = 1000, remoteUpdatedAt = 2000))
    }

    @Test
    fun `local wins on tie or when newer`() {
        assertFalse(SyncLogic.shouldApplyRemote(localUpdatedAt = 2000, remoteUpdatedAt = 2000))
        assertFalse(SyncLogic.shouldApplyRemote(localUpdatedAt = 3000, remoteUpdatedAt = 2000))
    }

    @Test
    fun `timestamp round-trips through millis`() {
        val millis = 1_753_900_123_456L
        val ts = SyncLogic.millisToTimestamp(millis)
        assertEquals(millis, SyncLogic.timestampToMillis(ts))
    }

    @Test
    fun `negative-free nanos stay in range`() {
        val ts = SyncLogic.millisToTimestamp(1_753_900_123_999L)
        assertTrue(ts.nanoseconds in 0..999_999_999)
    }
}
