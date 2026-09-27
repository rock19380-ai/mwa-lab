package dev.mwalab.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.app.MwaLabComposition
import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabSigningServiceInstrumentedTest {
    @Test
    fun protectedSignerSurvivesRestartBoundaryAndVerifies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MwaLabComposition.identityRepository(context)
        val signingService = MwaLabComposition.signingService(context)
        val identity = repository.getOrCreate()
        val message = "mwa-lab-phase2-instrumented".encodeToByteArray()
        val signature = signingService.sign(message)

        assertArrayEquals(identity.publicKeyBytes(), signingService.publicIdentity().publicKeyBytes())
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(identity.publicKeyBytes(), 0))
        verifier.update(message, 0, message.size)
        assertTrue(verifier.verifySignature(signature))
    }
}
