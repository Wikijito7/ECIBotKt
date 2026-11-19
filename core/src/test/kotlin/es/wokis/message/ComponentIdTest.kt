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
}
