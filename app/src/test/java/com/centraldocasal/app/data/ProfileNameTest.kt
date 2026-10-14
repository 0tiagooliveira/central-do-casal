package com.centraldocasal.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNameTest {
    @Test fun firestoreNameRecoversAccountWithoutAuthDisplayName() {
        assertEquals("Rhayra", effectiveProfileName("  Rhayra ", null))
    }
    @Test fun fallsBackToFirebaseNameWhenProfileHasNoDisplayName() {
        assertEquals("Tiago", effectiveProfileName(null, "Tiago"))
        assertEquals("Tiago", effectiveProfileName(" ", "Tiago"))
    }
    @Test fun emptyNamesDoNotProduceBrokenReminderOwnerIdentity() {
        assertEquals("Meu perfil", effectiveProfileName(null, null))
    }
}
