package dev.mwalab.identity

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertTrue
import org.junit.Test

class Ed25519SigningTest {
    @Test
    fun signatureVerifiesAgainstGeneratedPublicKey() {
        val generated = Ed25519IdentityMaterial.generate()
        val message = "phase2-signing".encodeToByteArray()
        try {
            val signature = Ed25519IdentityMaterial.sign(generated.privateKeySeed, message)
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(generated.publicKey, 0))
            verifier.update(message, 0, message.size)
            assertTrue(verifier.verifySignature(signature))
        } finally {
            generated.privateKeySeed.fill(0)
        }
    }
}
