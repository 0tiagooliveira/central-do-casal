package com.centraldocasal.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SosFreshLocationTest {
    @Test fun acceptsOnlyRecentPlausibleCoordinatesForOptionalSosLocation() {
        val now=1_800_000_000_000L
        assertNull(recentSosLocation(null,now))
        assertNotNull(recentSosLocation(SafetyLocation(-25.45,-49.30,now-45_000),now))
        assertNull(recentSosLocation(SafetyLocation(-25.45,-49.30,now-95_000),now))
        assertNull(recentSosLocation(SafetyLocation(-25.45,-49.30,now+1_000),now))
        assertNull(recentSosLocation(SafetyLocation(100.0,-49.30,now),now))
        assertNull(recentSosLocation(SafetyLocation(-25.45,190.0,now),now))
        assertNull(recentSosLocation(SafetyLocation(Double.NaN,-49.30,now),now))
    }
}
