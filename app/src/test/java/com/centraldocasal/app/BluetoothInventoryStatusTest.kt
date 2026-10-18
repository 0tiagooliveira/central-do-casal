package com.centraldocasal.app

import org.junit.Assert.*
import org.junit.Test

/** A blank scan result is not the same as "no paired devices" before scanning. */
class BluetoothInventoryStatusTest {
    @Test fun initialStateDoesNotClaimScanningFinished() {
        val message = bluetoothInventoryMessage(loaded=false, failed=false, count=0)
        assertTrue(message.contains("Toque em"))
        assertFalse(message.startsWith("Nenhum aparelho"))
    }

    @Test fun emptySuccessfulScanCanTruthfullySayNone() {
        val message = bluetoothInventoryMessage(loaded=true, failed=false, count=0)
        assertTrue(message.startsWith("Nenhum aparelho"))
    }

    @Test fun failureExplainsRetryInsteadOfInventingAnEmptyScan() {
        val message = bluetoothInventoryMessage(loaded=false, failed=true, count=0)
        assertTrue(message.contains("Não foi possível"))
        assertTrue(message.contains("tente novamente"))
    }

    @Test fun positiveResultHasNoFalseOfflineStatus() {
        val message = bluetoothInventoryMessage(loaded=true, failed=false, count=2)
        assertTrue(message.contains("2 aparelho"))
        assertFalse(message.contains("Sem resposta"))
    }
}
