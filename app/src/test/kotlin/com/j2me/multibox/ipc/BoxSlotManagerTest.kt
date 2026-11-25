package com.j2me.multibox.ipc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoxSlotManagerTest {

    @Test
    fun test_allocate_up_to_8_slots_9th_returns_null() {
        val manager = BoxSlotManager(maxSlots = 8)
        for (i in 1..8) {
            val slot = manager.allocateSlot("box-$i")
            assertEquals(i, slot)
        }
        assertNull(manager.allocateSlot("box-9"))
    }

    @Test
    fun test_allocate_same_box_returns_existing_slot() {
        val manager = BoxSlotManager(maxSlots = 8)
        val first = manager.allocateSlot("box-a")
        val second = manager.allocateSlot("box-a")
        assertEquals(first, second)
    }

    @Test
    fun test_release_slot_allows_reallocation() {
        val manager = BoxSlotManager(maxSlots = 2)
        val s1 = manager.allocateSlot("box-1")
        val s2 = manager.allocateSlot("box-2")
        assertEquals(1, s1)
        assertEquals(2, s2)
        assertNull(manager.allocateSlot("box-3"))

        manager.releaseSlot("box-1")
        assertEquals(1, manager.allocateSlot("box-3"))
    }
}
