package com.j2me.multibox.ipc

/**
 * Tracks process slot allocation (:box_1 through :box_8) for active boxes.
 */
class BoxSlotManager(private val maxSlots: Int = 8) {

    private val boxToSlot = LinkedHashMap<String, Int>()
    private val slotToBox = HashMap<Int, String>()

    @Synchronized
    fun allocateSlot(boxId: String): Int? {
        boxToSlot[boxId]?.let { return it }

        for (slot in 1..maxSlots) {
            if (!slotToBox.containsKey(slot)) {
                boxToSlot[boxId] = slot
                slotToBox[slot] = boxId
                return slot
            }
        }
        return null
    }

    @Synchronized
    fun releaseSlot(boxId: String) {
        val slot = boxToSlot.remove(boxId)
        if (slot != null) {
            slotToBox.remove(slot)
        }
    }

    @Synchronized
    fun getSlotForBox(boxId: String): Int? = boxToSlot[boxId]

    @Synchronized
    fun activeCount(): Int = boxToSlot.size
}
