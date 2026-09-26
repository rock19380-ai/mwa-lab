package dev.mwalab.mwa.authorization

import org.bouncycastle.util.encoders.Base64
import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizedAccount
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.security.NetworkDecision
import dev.mwalab.security.NetworkPolicy

sealed interface LabAuthorizationDecision {
    data class Granted(
        val account: AuthorizedAccount,
        val authorizationScope: ByteArray,
    ) : LabAuthorizationDecision

    data object UnsupportedChain : LabAuthorizationDecision
    data object UnsupportedOptionalFeatures : LabAuthorizationDecision
    data object UnsupportedSignIn : LabAuthorizationDecision
    data object RequestedAddressUnavailable : LabAuthorizationDecision
}

class LabAuthorizationPolicy(
    private val identityRepository: IdentityRepository,
) {
    suspend fun evaluate(
        chain: String?,
        requestedFeatures: Array<out String>?,
        requestedAddresses: Array<out String>?,
        hasSignInPayload: Boolean,
    ): LabAuthorizationDecision {
        if (NetworkPolicy.evaluate(chain) !is NetworkDecision.Allowed) {
            return LabAuthorizationDecision.UnsupportedChain
        }

        if (!requestedFeatures.isNullOrEmpty()) {
            return LabAuthorizationDecision.UnsupportedOptionalFeatures
        }

        if (hasSignInPayload) {
            return LabAuthorizationDecision.UnsupportedSignIn
        }

        val identity = identityRepository.getOrCreate()
        val publicKey = identity.publicKeyBytes()

        if (!requestedAddresses.isNullOrEmpty()) {
            val addressMatched = requestedAddresses.any { encodedAddress ->
                runCatching {
                    Base64.decode(encodedAddress)
                }.getOrNull()?.contentEquals(publicKey) == true
            }

            if (!addressMatched) {
                return LabAuthorizationDecision.RequestedAddressUnavailable
            }
        }

        return LabAuthorizationDecision.Granted(
            account = AuthorizedAccount(
                publicKey,
                identity.displayAddress,
                identity.displayAddressFormat,
                identity.label,
                null,
                arrayOf(identity.chain),
                emptyArray(),
            ),
            authorizationScope = AUTHORIZATION_SCOPE.copyOf(),
        )
    }

    companion object {
        private val AUTHORIZATION_SCOPE =
            "mwa-lab:phase1:devnet:v1".encodeToByteArray()
    }
}
