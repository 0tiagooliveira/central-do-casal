package com.centraldocasal.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Layout decisions can be checked without screenshots or a network connection. */
class HomeCareLayoutTest {
    @Test fun phoneUsesTwoReadableColumns() {
        assertEquals(2, homeCareColumns(355f, 1f))
        assertEquals(2, homeCareColumns(325f, 1.3f))
    }

    @Test fun narrowPhoneAndLargeFontsUseOneColumn() {
        assertEquals(1, homeCareColumns(290f, 1f))
        assertEquals(1, homeCareColumns(380f, 1.6f))
        assertEquals(1, homeCareColumns(850f, 2f))
    }

    @Test fun largeScreenUsesFourColumnsOnlyAtStandardScale() {
        assertEquals(4, homeCareColumns(850f, 1f))
        assertEquals(2, homeCareColumns(850f, 1.3f))
        assertEquals(2, homeCareColumns(550f, 1f))
    }
}
