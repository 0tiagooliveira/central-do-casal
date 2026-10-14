package com.centraldocasal.app.data

import org.junit.Assert.*
import org.junit.Test

class SyncAccessTest {
    @Test fun newHouseholdWaitsForServerConfirmation() {
        assertFalse(confirmedProfile(true, true))
        assertFalse(confirmedProfile(true, false))
        assertFalse(confirmedProfile(false, true))
        assertTrue(confirmedProfile(false, false))
    }
    @Test fun unrelatedSuccessfulStreamDoesNotHideDeniedStream() {
        val access = SyncAccess(); access.fail("baskets", "denied")
        access.confirm("shared"); assertEquals("denied", access.error)
        access.confirm("baskets"); assertNull(access.error)
    }
    @Test fun allFailedStreamsMustRecoverBeforeErrorDisappears() {
        val access = SyncAccess(); access.fail("personal", "private denied"); access.fail("shared", "shared denied")
        access.confirm("personal"); assertEquals("shared denied", access.error)
        access.confirm("shared"); assertNull(access.error)
    }
    @Test fun offlineKnownHouseholdWorksButPendingMembershipIsNeverTrusted() {
        assertTrue(confirmedProfile(false, true, previouslyConfirmed = true))
        assertFalse(confirmedProfile(true, true, previouslyConfirmed = true))
        assertFalse(confirmedProfile(false, true, previouslyConfirmed = false))
    }
}
