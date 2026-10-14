package com.centraldocasal.app

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLocationProviderTest {
    @Test fun coarseOnlyLocationNeverRequestsGps() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER),
            liveLocationProviderCandidates(fineGranted=false,coarseGranted=true)
        )
    }

    @Test fun fineLocationCanUseGpsAndNetworkFallback() {
        assertEquals(
            listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER),
            liveLocationProviderCandidates(fineGranted=true,coarseGranted=false)
        )
    }

    @Test fun withoutLocationPermissionNoProviderIsRequested() {
        assertTrue(liveLocationProviderCandidates(fineGranted=false,coarseGranted=false).isEmpty())
    }
}
