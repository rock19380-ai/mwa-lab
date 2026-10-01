package dev.mwalab.faults

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersistentFaultSelectionRepository(
    private val store: FaultSelectionStore,
) : FaultSelectionRepository {
    private var persistedId: String? = runCatching { store.read() }.getOrNull()
    private val mutableSelected = MutableStateFlow(
        FaultCatalog.find(persistedId) ?: FaultCatalog.get(FaultId.NORMAL),
    )
    override val selected: StateFlow<FaultProfile> = mutableSelected.asStateFlow()

    @Synchronized
    override fun select(id: FaultId) {
        val profile = FaultCatalog.get(id)
        if (profile == mutableSelected.value && persistedId == id.stableId) return
        check(store.write(id.stableId)) { "Fault selection could not be persisted" }
        persistedId = id.stableId
        mutableSelected.value = profile
    }
}
