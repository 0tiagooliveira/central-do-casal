package com.centraldocasal.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class HomeAssistantPreviewTest {
    @Test fun onlyHttpsRootEndpointIsAllowed() {
        assertEquals("https://casa.example.org/api/states",
            HomeAssistantProbe.endpoint("https://casa.example.org").toString())
        for(invalid in listOf(
            "http://casa.example.org",
            "https://user:secret@casa.example.org",
            "https://casa.example.org/path",
            "https://casa.example.org/?token=secret",
            "javascript:alert(1)"
        )) {
            try {
                HomeAssistantProbe.endpoint(invalid)
                fail("URL insegura aceita")
            } catch (expected: IllegalArgumentException) { /* input rejected */ }
        }
    }

    @Test fun controlUsesRealServiceEndpointAndRejectsSensitiveDevices() {
        assertEquals("https://casa.example.org/api/services/light/turn_on",
            HomeAssistantProbe.commandEndpoint("https://casa.example.org","light.sala",true).toString())
        assertEquals("https://casa.example.org:8123/api/services/switch/turn_off",
            HomeAssistantProbe.commandEndpoint("https://casa.example.org:8123","switch.tomada",false).toString())
        for(id in listOf("lock.front_door","cover.garage","alarm_control_panel.home",
            "climate.bedroom","automation.open_door","light.bad/path","switch.foo?all=1")) {
            try {
                HomeAssistantProbe.commandEndpoint("https://casa.example.org",id,true)
                fail("Entidade sensível ou malformada não pode ser controlada: " + id)
            } catch (expected: IllegalArgumentException) { /* bloqueado */ }
        }
        try {
            HomeAssistantProbe.commandEndpoint("http://casa.example.org","light.sala",true)
            fail("Credenciais não podem ser enviadas por HTTP.")
        } catch (expected: IllegalArgumentException) { /* TLS obrigatório */ }
    }

    @Test fun parsedStatesExcludePeopleLocationAndOtherSensitiveDomains() {
        val result=HomeAssistantProbe.parseStates(
            """[
                {"entity_id":"light.sala","state":"on","attributes":{"friendly_name":"Luz da sala"}},
                {"entity_id":"switch.tomada","state":"off","attributes":{}},
                {"entity_id":"person.rh","state":"home","attributes":{"latitude":1,"longitude":2}},
                {"entity_id":"device_tracker.telefone","state":"home","attributes":{}},
                {"entity_id":"sensor.entrada","state":"57","attributes":{}}
            ]"""
        )
        assertEquals(listOf("light.sala","switch.tomada"),result.map { it.id })
        assertEquals("Luz da sala",result.first().title)
        assertEquals("off",result.last().state)
    }
}
