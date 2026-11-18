package es.wokis.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComponentIdTest {

    @Test
    fun `Given a bare value When resolved Then return the matching id`() {
        assertEquals(ComponentId.PLAYER_PAUSE, ComponentId.fromCustomId("player_pause"))
        assertEquals(ComponentId.QUEUE_NEXT, ComponentId.fromCustomId("queue_next"))
    }

    @Test
    fun `Given a payload suffix When resolved Then ignore everything after the separator`() {
        assertEquals(ComponentId.PLAYER_RESUME, ComponentId.fromCustomId("player_resume::state-42"))
        assertEquals(ComponentId.RADIO_LIST_NEXT, ComponentId.fromCustomId("radio_list_next::page=3"))
        assertEquals(ComponentId.PLAYER_SKIP, ComponentId.fromCustomId("player_skip::"))
    }

    @Test
    fun `Given an unknown or empty value When resolved Then return null`() {
        assertNull(ComponentId.fromCustomId("player_unknown"))
        assertNull(ComponentId.fromCustomId("::only-payload"))
        assertNull(ComponentId.fromCustomId(""))
    }

    @Test
    fun `Given every id When resolved with a payload Then round trip to itself`() {
        ComponentId.entries.forEach { id ->
            assertEquals(id, ComponentId.fromCustomId("${id.value}::state"), id.name)
        }
    }

    @Test
    fun `Given a component id When a payload is attached Then join both with the wire separator`() {
        assertEquals("queue_next::2", ComponentId.QUEUE_NEXT.customId("2"))
        assertEquals("player_pause", ComponentId.PLAYER_PAUSE.customId())
    }

    @Test
    fun `Given a blank payload When building a custom id Then emit the bare value`() {
        assertEquals("queue_previous", ComponentId.QUEUE_PREVIOUS.customId(""))
        assertEquals("queue_previous", ComponentId.QUEUE_PREVIOUS.customId(null))
    }

    @Test
    fun `Given a custom id When the payload is read Then return only the state suffix`() {
        assertEquals("2", ComponentId.payloadFrom("queue_next::2"))
        assertEquals("page=3", ComponentId.payloadFrom("radio_list_next::page=3"))
        assertNull(ComponentId.payloadFrom("queue_next"))
        assertNull(ComponentId.payloadFrom("queue_next::"))
    }
}
