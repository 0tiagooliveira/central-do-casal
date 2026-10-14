package com.centraldocasal.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLocationTest {
    private val now = 1_800_000_000_000L

    @Test fun currentLocationNeedsRecentFixAndBothExpiryWindows() {
        val fresh = LiveLocation(
            uid = "userA",
            latitude = -25.0,
            longitude = -49.0,
            capturedAt = now - 15_000L,
            expiresAt = now + 60_000L,
            sessionEndsAt = now + 120_000L
        )
        assertTrue(fresh.isCurrent(now))
        assertFalse(fresh.isCurrent(now + 61_000L))
        assertFalse(fresh.copy(expiresAt = now + 1_000_000L).isCurrent(now + 120_000L))
        assertFalse(fresh.copy(capturedAt = now - 180_001L).isCurrent(now))
        assertFalse(fresh.copy(capturedAt = 0L).isCurrent(now))
        assertFalse(fresh.copy(capturedAt = now + 20_000L).isCurrent(now))
        assertFalse(fresh.copy(latitude = 91.0).isCurrent(now))
        assertFalse(fresh.copy(longitude = -181.0).isCurrent(now))
        assertFalse(fresh.copy(capturedAt = now - 90_001L).isCurrent(now))
    }
}
