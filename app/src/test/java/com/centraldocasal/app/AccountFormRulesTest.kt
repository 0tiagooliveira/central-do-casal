package com.centraldocasal.app

import org.junit.Assert.*
import org.junit.Test

class AccountFormRulesTest {
    @Test fun resetRequiresUsableEmail() {
        assertFalse(accountEmailLooksUsable(""))
        assertFalse(accountEmailLooksUsable("nome"))
        assertFalse(accountEmailLooksUsable("@dominio.com"))
        assertFalse(accountEmailLooksUsable("nome@"))
        assertFalse(accountEmailLooksUsable("nome com espaço@dominio.com"))
        assertTrue(accountEmailLooksUsable(" nome+casal@exemplo.com "))
    }

    @Test fun registrationRequiresNameAndEightCharacterPassword() {
        assertFalse(accountFormCanSubmit("casal@exemplo.com","1234567",true,"Rhayra"))
        assertFalse(accountFormCanSubmit("casal@exemplo.com","12345678",true,"  "))
        assertTrue(accountFormCanSubmit("casal@exemplo.com","12345678",true,"Rhayra"))
    }

    @Test fun loginDoesNotRequireRegistrationName() {
        assertTrue(accountFormCanSubmit("casal@exemplo.com","senha_existente",false,""))
        assertFalse(accountFormCanSubmit("inválido","senha_existente",false,""))
    }
}
