package dev.mwalab.faults

import kotlinx.coroutines.flow.StateFlow

/** Private application authority: one selected profile, never a list of active rules. */
interface FaultSelectionRepository {
    val selected: StateFlow<FaultProfile>
    fun select(id: FaultId)
}

/** Minimal persistence seam for deterministic JVM tests and private Android storage. */
interface FaultSelectionStore {
    fun read(): String?
    fun write(stableId: String): Boolean
}
