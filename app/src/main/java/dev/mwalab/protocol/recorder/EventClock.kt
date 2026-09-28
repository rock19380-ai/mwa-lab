package dev.mwalab.protocol.recorder

fun interface EventClock {
    fun nowEpochMillis(): Long
}

object SystemEventClock : EventClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
