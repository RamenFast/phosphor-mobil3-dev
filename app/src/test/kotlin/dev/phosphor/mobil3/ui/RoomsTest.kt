package dev.phosphor.mobil3.ui

import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class RoomsTest {
    @Test fun threeRoomsShipWithStableIds() {
        assertEquals(listOf("amoled", "blossom", "glass"), Rooms.map { it.id })
        assertEquals(listOf("Blossom AMOLED", "Blossom Light", "Liquid Glass"), Rooms.map { it.label })
    }

    @Test fun removedOrUnknownStoredRoomFallsBackToBlossomAmoled() {
        for (gone in listOf("blossom_dark", "light", "dark", "chromacore", "basalt", "afterglow",
            "stonework95", "paper", "amber", "fable", "", "nope")) {
            assertSame(Amoled, paletteById(gone))
        }
        for (room in Rooms) assertSame(room, paletteById(room.id))
    }
}
