package dev.mwalab.faults

import android.content.Context

/** Only the stable ID is stored in this app's private preferences. */
class PrivatePreferencesFaultSelectionStore(context: Context) : FaultSelectionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "phase6_fault_selection", Context.MODE_PRIVATE,
    )

    override fun read(): String? = preferences.getString("active_fault_id", null)

    override fun write(stableId: String): Boolean =
        preferences.edit().putString("active_fault_id", stableId).commit()
}
