package dev.mwalab.identity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Ed25519IdentityMaterialTest {
    @Test
    fun generatedIdentityIsARealEd25519Keypair() {
        val generated = Ed25519IdentityMaterial.generate()
        try {
            assertEquals(32, generated.privateKeySeed.size)
            assertEquals(32, generated.publicKey.size)

            val restoredPublic =
                Ed25519IdentityMaterial.publicKeyFromPrivateSeed(generated.privateKeySeed)
            assertArrayEquals(generated.publicKey, restoredPublic)

            val address = Ed25519IdentityMaterial.displayAddress(generated.publicKey)
            assertNotEquals("", address)
        } finally {
            generated.privateKeySeed.fill(0)
        }
    }
}
