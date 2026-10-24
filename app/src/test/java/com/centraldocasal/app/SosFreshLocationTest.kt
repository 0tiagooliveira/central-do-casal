package com.centraldocasal.app

import org.junit.Assert.*
import com.centraldocasal.app.domain.Record
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
    @Test fun onlyFreshPartnerSosCanTriggerAnUrgentLocalNotification() {
        val now=1_800_000_000_000L
        fun sos(owner:String,whenCreated:Long)=Record("sos-1","safetyAlert",fields=mapOf(
            "ownerUid" to owner,
            "ownerName" to "Parceiro",
            "createdAt" to whenCreated
        ))
        assertTrue(freshPartnerSosAlert(sos("other",now-30_000),"me",now))
        assertTrue(freshPartnerSosAlert(sos("other",now-15*60_000),"me",now))
        assertFalse(freshPartnerSosAlert(sos("other",now-15*60_000-1),"me",now))
        assertFalse(freshPartnerSosAlert(sos("other",now+1),"me",now))
        assertFalse(freshPartnerSosAlert(sos("me",now-30_000),"me",now))
        assertFalse(freshPartnerSosAlert(sos("",now-30_000),"me",now))
        assertFalse(freshPartnerSosAlert(sos("other",0),"me",now))
        assertFalse(freshPartnerSosAlert(sos("other",now-30_000),"",now))
        assertFalse(freshPartnerSosAlert(Record("t","task",fields=mapOf("createdAt" to now)), "me",now))
    }


    @Test fun foregroundLocationRequiresCurrentConsentAndExpiresAfterOneHour() {
        val start=100_000L
        assertTrue(activeForegroundShareSession(true,start,true,true,start))
        assertTrue(activeForegroundShareSession(true,start,true,true,start+59*60_000L))
        assertFalse(activeForegroundShareSession(true,start,true,true,start+60*60_000L))
        assertFalse(activeForegroundShareSession(true,start,true,true,start-1L))
        assertFalse(activeForegroundShareSession(false,start,true,true,start+1000L))
        assertFalse(activeForegroundShareSession(true,start,false,true,start+1000L))
        assertFalse(activeForegroundShareSession(true,start,true,false,start+1000L))
        assertFalse(activeForegroundShareSession(true,0L,true,true,start+1000L))
    }

}
