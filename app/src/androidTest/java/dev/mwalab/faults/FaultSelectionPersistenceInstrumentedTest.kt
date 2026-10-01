package dev.mwalab.faults

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaultSelectionPersistenceInstrumentedTest {
    @Test
    fun privatePreferencesSurviveRepositoryRecreationAndCorruptionFailsSafe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase6_fault_selection_instrumented_test"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val first = PersistentFaultSelectionRepository(PrivatePreferencesFaultSelectionStore(context, name))
            assertEquals(FaultId.NORMAL, first.selected.value.id)
            first.select(FaultId.SIGN_REJECT)
            assertEquals("FAULT_SIGN_REJECT", preferences.getString("active_fault_id", null))

            val recreated = PersistentFaultSelectionRepository(PrivatePreferencesFaultSelectionStore(context, name))
            assertEquals(FaultId.SIGN_REJECT, recreated.selected.value.id)
            recreated.select(FaultId.NORMAL)
            assertEquals(FaultId.NORMAL,
                PersistentFaultSelectionRepository(PrivatePreferencesFaultSelectionStore(context, name)).selected.value.id)

            preferences.edit().putInt("active_fault_id", 42).commit()
            val corrupt = PersistentFaultSelectionRepository(PrivatePreferencesFaultSelectionStore(context, name))
            assertEquals(FaultId.NORMAL, corrupt.selected.value.id)
            corrupt.select(FaultId.NORMAL)
            assertEquals("NORMAL", preferences.getString("active_fault_id", null))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
