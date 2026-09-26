package dev.mwalab.identity

import com.funkatronics.encoders.Base58
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom

internal object Ed25519IdentityMaterial {
    data class Generated(
        val privateKeySeed: ByteArray,
        val publicKey: ByteArray,
    )

    fun generate(): Generated {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters

        return Generated(
            privateKeySeed = privateKey.encoded,
            publicKey = publicKey.encoded,
        )
    }

    fun publicKeyFromPrivateSeed(privateKeySeed: ByteArray): ByteArray {
        require(privateKeySeed.size == PRIVATE_KEY_SEED_SIZE) {
            "Ed25519 private seed must be exactly $PRIVATE_KEY_SEED_SIZE bytes"
        }

        val privateKey = Ed25519PrivateKeyParameters(privateKeySeed, 0)
        return privateKey.generatePublicKey().encoded
    }

    fun displayAddress(publicKey: ByteArray): String {
        require(publicKey.size == PUBLIC_KEY_SIZE) {
            "Ed25519 public key must be exactly $PUBLIC_KEY_SIZE bytes"
        }
        return Base58.encodeToString(publicKey)
    }

    private const val PRIVATE_KEY_SEED_SIZE = 32
    private const val PUBLIC_KEY_SIZE = 32
}
