package dev.mwalab.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import dev.mwalab.signing.LabSigningService

class AndroidKeystoreIdentityRepository(
    context: Context,
    private val preferencesName: String = DEFAULT_PREFERENCES_NAME,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : IdentityRepository, LabSigningService {
    private val appContext = context.applicationContext
    private val lock = Any()

    override suspend fun getOrCreate(): TestEndpointIdentity = withContext(Dispatchers.IO) {
        synchronized(lock) {
            when (storedState()) {
                StoredState.EMPTY -> createAndStore()
                StoredState.COMPLETE -> loadExisting()
                StoredState.PARTIAL -> throw IdentityStorageException(
                    "Protected Lab identity state is incomplete; refusing to regenerate silently",
                )
            }
        }
    }

    override suspend fun reset(): TestEndpointIdentity = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val cleared = preferences().edit().clear().commit()
            if (!cleared) {
                throw IdentityStorageException("Unable to invalidate the existing Lab identity")
            }
            createAndStore()
        }
    }

    override suspend fun publicIdentity(): TestEndpointIdentity = getOrCreate()

    override suspend fun sign(message: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        synchronized(lock) {
            signWithStoredIdentity(message)
        }
    }


    private fun signWithStoredIdentity(message: ByteArray): ByteArray {
        if (storedState() != StoredState.COMPLETE) {
            throw IdentityStorageException("Protected Lab identity is unavailable for signing")
        }
        val prefs = preferences()
        val ciphertext = decodeRequired(prefs.getString(KEY_CIPHERTEXT, null))
        val iv = decodeRequired(prefs.getString(KEY_IV, null))
        val storedPublicKey = decodeRequired(prefs.getString(KEY_PUBLIC_KEY, null))
        val privateKeySeed = try {
            decrypt(ciphertext, iv)
        } catch (t: Throwable) {
            throw IdentityStorageException("Unable to decrypt protected Lab identity", t)
        }
        try {
            val derivedPublicKey = Ed25519IdentityMaterial.publicKeyFromPrivateSeed(privateKeySeed)
            if (!derivedPublicKey.contentEquals(storedPublicKey)) {
                throw IdentityStorageException(
                    "Protected Lab identity integrity check failed; refusing to sign",
                )
            }
            return Ed25519IdentityMaterial.sign(privateKeySeed, message)
        } finally {
            privateKeySeed.fill(0)
        }
    }

    private fun createAndStore(): TestEndpointIdentity {
        val generated = Ed25519IdentityMaterial.generate()
        try {
            val encrypted = encrypt(generated.privateKeySeed)
            val stored = preferences().edit()
                .putString(KEY_CIPHERTEXT, encode(encrypted.ciphertext))
                .putString(KEY_IV, encode(encrypted.iv))
                .putString(KEY_PUBLIC_KEY, encode(generated.publicKey))
                .commit()

            if (!stored) {
                throw IdentityStorageException("Unable to persist protected Lab identity")
            }

            return toPublicIdentity(generated.publicKey)
        } finally {
            generated.privateKeySeed.fill(0)
        }
    }

    private fun loadExisting(): TestEndpointIdentity {
        val prefs = preferences()
        val ciphertext = decodeRequired(prefs.getString(KEY_CIPHERTEXT, null))
        val iv = decodeRequired(prefs.getString(KEY_IV, null))
        val storedPublicKey = decodeRequired(prefs.getString(KEY_PUBLIC_KEY, null))

        val privateKeySeed = try {
            decrypt(ciphertext, iv)
        } catch (t: Throwable) {
            throw IdentityStorageException("Unable to decrypt protected Lab identity", t)
        }

        try {
            val derivedPublicKey = try {
                Ed25519IdentityMaterial.publicKeyFromPrivateSeed(privateKeySeed)
            } catch (t: Throwable) {
                throw IdentityStorageException("Protected Lab identity material is invalid", t)
            }

            if (!derivedPublicKey.contentEquals(storedPublicKey)) {
                throw IdentityStorageException(
                    "Protected Lab identity integrity check failed; refusing to continue",
                )
            }

            return toPublicIdentity(derivedPublicKey)
        } finally {
            privateKeySeed.fill(0)
        }
    }

    private fun toPublicIdentity(publicKey: ByteArray): TestEndpointIdentity =
        TestEndpointIdentity(
            publicKey = publicKey,
            displayAddress = Ed25519IdentityMaterial.displayAddress(publicKey),
        )

    private fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        return EncryptedPayload(
            iv = cipher.iv.copyOf(),
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateEncryptionKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun preferences() =
        appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    private fun storedState(): StoredState {
        val prefs = preferences()
        val present = listOf(
            prefs.contains(KEY_CIPHERTEXT),
            prefs.contains(KEY_IV),
            prefs.contains(KEY_PUBLIC_KEY),
        )

        return when {
            present.none { it } -> StoredState.EMPTY
            present.all { it } -> StoredState.COMPLETE
            else -> StoredState.PARTIAL
        }
    }

    private fun encode(value: ByteArray): String =
        Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decodeRequired(value: String?): ByteArray {
        if (value.isNullOrEmpty()) {
            throw IdentityStorageException("Protected Lab identity state is missing")
        }

        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (t: IllegalArgumentException) {
            throw IdentityStorageException("Protected Lab identity state is malformed", t)
        }
    }

    private data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private enum class StoredState {
        EMPTY,
        COMPLETE,
        PARTIAL,
    }

    companion object {
        const val DEFAULT_PREFERENCES_NAME = "mwa_lab_identity"
        private const val DEFAULT_KEY_ALIAS = "dev.mwalab.lab_identity_aes_v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_SIZE_BITS = 256

        private const val KEY_CIPHERTEXT = "identity_ciphertext_v1"
        private const val KEY_IV = "identity_iv_v1"
        private const val KEY_PUBLIC_KEY = "identity_public_key_v1"
    }
}

class IdentityStorageException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
