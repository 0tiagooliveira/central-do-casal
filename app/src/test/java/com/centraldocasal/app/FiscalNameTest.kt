package com.centraldocasal.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FiscalNameTest {
    @Test fun expandsReceiptNamesCheckedAgainstPublicCatalogues() {
        assertEquals("Queijo ralado Parmital 40 g", suggestedFiscalName("Qjo Ral Parmital 40G"))
        assertEquals("Copo 150 mL com tampa bolha — pacote com 50 unidades", suggestedFiscalName("Copo + Tampa Bolha 150ML C50"))
        assertEquals("Batata palito pré-frita congelada Rapmix 2 kg", suggestedFiscalName("Batata Rapmix 2KG"))
        assertEquals("Tempero natural Mix Kitano 40 g — variante a confirmar", suggestedFiscalName("Temp Kitano Mix 40G"))
        org.junit.Assert.assertTrue(isGenericCatalogBase("Temperos"))
        org.junit.Assert.assertFalse(isGenericCatalogBase("Tempero natural Mix Kitano 40 g"))
    }
}
