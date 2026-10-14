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
