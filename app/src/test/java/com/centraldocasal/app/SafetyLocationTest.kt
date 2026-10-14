package com.centraldocasal.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyLocationTest {
    @Test fun manualPositionMustBeFreshAndWithinGeographicBounds() {
        val now = 1_800_000_000_000L
        val location = SafetyLocation(-25.0, -49.0, now - 15_000L)
        assertTrue(location.isFresh(now))
        assertFalse(location.copy(capturedAt = now - 90_001L).isFresh(now))
        assertFalse(location.copy(capturedAt = now + 1_000L).isFresh(now))
        assertFalse(location.copy(latitude = 92.0).isFresh(now))
        assertFalse(location.copy(longitude = 181.0).isFresh(now))
    }
}
