package com.centraldocasal.app

import com.centraldocasal.app.data.SessionState
import com.centraldocasal.app.domain.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MomentPhotoPathTest {
    private val session=SessionState(uid="alice",name="Alice",household="home",verified=true)

    @Test fun sharedPhotoUsesHouseholdLocationWithoutPublicLink() {
        val moment=Record(id="sushi",kind="moment",isPrivate=false,fields=mapOf("title" to "Noite do sushi"))
        assertEquals("households/home/moments/sushi/alice-image.jpg",
            momentPhotoPath(session,moment,"image"))
    }

    @Test fun privatePhotoStaysInOwnersNamespace() {
        val moment=Record(id="private",kind="moment",isPrivate=true,fields=mapOf("title" to "Particular"))
        assertEquals("users/alice/moments/private/alice-image.jpg",
            momentPhotoPath(session,moment,"image"))
    }

    @Test fun localOnlyModeCannotPretendToShareCloudPhotos() {
        val moment=Record(id="sushi",kind="moment",fields=mapOf("title" to "Noite do sushi"))
        assertThrows(IllegalArgumentException::class.java) {
            momentPhotoPath(session.copy(local=true),moment,"image")
        }
    }

    @Test fun pathInjectionIsRejected() {
        val moment=Record(id="../other",kind="moment",fields=mapOf("title" to "Teste"))
        assertThrows(IllegalArgumentException::class.java) {
            momentPhotoPath(session,moment,"image")
        }
    }
}
