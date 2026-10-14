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

    @Test fun bluetoothRegistrationChecksExistingActiveAndArchivedNames() {
        val records = listOf(
            Record("one", "smartDevice", fields = mapOf(
                "title" to "Abajur",
                "provider" to "Bluetooth",
                "bluetoothName" to " Fone da Sala ",
                "archived" to true
            )),
            Record("two", "smartDevice", fields = mapOf(
                "title" to "Outro aparelho",
                "provider" to "Alexa",
                "bluetoothName" to "Caixa de som"
            )),
            Record("task", "task", fields = mapOf(
                "title" to "Caixa de som", "provider" to "Bluetooth"
            ))
        )
        org.junit.Assert.assertTrue(bluetoothNameRegistered(records, "fone da sala"))
        org.junit.Assert.assertTrue(bluetoothNameRegistered(records, " FONE DA SALA "))
        org.junit.Assert.assertFalse(bluetoothNameRegistered(records, "Caixa de som"))
        org.junit.Assert.assertFalse(bluetoothNameRegistered(records, ""))
    }
}
