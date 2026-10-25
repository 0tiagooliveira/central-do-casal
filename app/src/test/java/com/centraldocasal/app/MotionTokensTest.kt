package com.centraldocasal.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTokensTest {
    @Test fun motionPreservesPurposeWhenEnabled() {
        assertEquals(140, motionDurationMillis(MotionKind.FEEDBACK, true, true))
        assertEquals(180, motionDurationMillis(MotionKind.NAVIGATION, true, true))
        assertEquals(220, motionDurationMillis(MotionKind.LAYOUT, true, true))
        assertEquals(400, motionDurationMillis(MotionKind.PROGRESS, true, true))
    }

    @Test fun personalMotionPreferenceDisablesAllAnimations() {
        MotionKind.entries.forEach { kind ->
            assertEquals(0, motionDurationMillis(kind, false, true))
        }
    }

    @Test fun androidDisabledAnimatorsOverridesPersonalPreference() {
        MotionKind.entries.forEach { kind ->
            assertEquals(0, motionDurationMillis(kind, true, false))
        }
    }

    @Test fun upstreamEaseOutIsResponsiveAndKeepsEndpoints() {
        org.junit.Assert.assertEquals(0f, centralEaseOut.transform(0f), 0.00001f)
        org.junit.Assert.assertEquals(1f, centralEaseOut.transform(1f), 0.00001f)
        org.junit.Assert.assertTrue(centralEaseOut.transform(0.5f) > 0.5f)
    }

    @Test fun twoDisabledInputsRemainDisabled() {
        MotionKind.entries.forEach { kind ->
            assertEquals(0, motionDurationMillis(kind, false, false))
        }
    }
}
