package dev.mwalab.identity

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreIdentityRepositoryInstrumentedTest {
    @Test
    fun identityPersistsAcrossRepositoryRestartAndResetChangesAuthority() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val preferencesName = "mwa_lab_identity_test_$suffix"
        val keyAlias = "dev.mwalab.test_identity_$suffix"

        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        try {
            val firstRepository = AndroidKeystoreIdentityRepository(
                context = context,
                preferencesName = preferencesName,
                keyAlias = keyAlias,
            )
            val first = firstRepository.getOrCreate()

            val restartedRepository = AndroidKeystoreIdentityRepository(
                context = context,
                preferencesName = preferencesName,
                keyAlias = keyAlias,
            )
            val afterRestart = restartedRepository.getOrCreate()

            assertEquals(first.displayAddress, afterRestart.displayAddress)

            val afterReset = restartedRepository.reset()
            assertNotEquals(first.displayAddress, afterReset.displayAddress)

            val afterSecondRestart = AndroidKeystoreIdentityRepository(
                context = context,
                preferencesName = preferencesName,
                keyAlias = keyAlias,
            ).getOrCreate()

            assertEquals(afterReset.displayAddress, afterSecondRestart.displayAddress)
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }
}
