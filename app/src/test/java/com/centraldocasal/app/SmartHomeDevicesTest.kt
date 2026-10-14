package com.centraldocasal.app

import com.centraldocasal.app.domain.Record
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartHomeDevicesTest {
    @Test fun archivedDevicesAreHiddenUntilRequestedAndRoomsStayFiltered() {
        val devices = listOf(
            Record("a", "smartDevice", fields = mapOf("title" to "Luz da cozinha", "room" to "Cozinha")),
            Record("b", "smartDevice", fields = mapOf("title" to "Tomada", "room" to "Quarto", "archived" to true)),
            Record("c", "smartDevice", fields = mapOf("title" to "Abajur", "room" to "Quarto")),
            Record("d", "task", fields = mapOf("title" to "Não é aparelho", "room" to "Quarto"))
        )
        assertEquals(listOf("a", "c"), smartHomeDevices(devices, false).map { it.id })
        assertEquals(listOf("c"), smartHomeDevices(devices, false, "Quarto").map { it.id })
        assertEquals(listOf("c", "b"), smartHomeDevices(devices, true, "Quarto").map { it.id })
        assertEquals(3, smartHomeDevices(devices, true).size)
    }
}
