package dev.mwalab.rpc

internal data class SignAndSendSubmission(
    val payload: ByteArray,
    val expectedSignature: ByteArray,
)

internal enum class SignAndSendFatalReason(val evidenceResult: String) {
    SIGNATURE_MISMATCH("rpc_signature_mismatch"),
    COMMITMENT_NOT_REACHED_BEFORE_NEXT("commitment_not_verified_before_next"),
    COMMITMENT_UNAVAILABLE_BEFORE_NEXT("commitment_verification_unavailable_before_next"),
    COMMITMENT_NOT_REACHED("commitment_not_verified"),
    COMMITMENT_UNAVAILABLE("commitment_verification_unavailable"),
}

internal sealed interface SignAndSendSubmissionResult {
    data class Submitted(
        val signatures: List<ByteArray>,
    ) : SignAndSendSubmissionResult

    data class NotSubmitted(
        val signatures: List<ByteArray?>,
    ) : SignAndSendSubmissionResult

    data class Fatal(
        val reason: SignAndSendFatalReason,
        val submittedCount: Int,
    ) : SignAndSendSubmissionResult

    data object Cancelled : SignAndSendSubmissionResult
}

internal class SignAndSendSubmissionExecutor(
    private val rpcGateway: DevnetRpcGateway,
) {
    suspend fun execute(
        transactions: List<SignAndSendSubmission>,
        options: DevnetSendOptions,
        isRequestCurrent: () -> Boolean,
    ): SignAndSendSubmissionResult {
        val submittedSignatures = MutableList<ByteArray?>(transactions.size) { null }
        val commitmentVerified = BooleanArray(transactions.size)

        for (index in transactions.indices) {
            if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled

            val transaction = transactions[index]
            when (val submission = rpcGateway.sendTransaction(transaction.payload, options)) {
                is DevnetRpcResult.Success -> {
                    if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled
                    if (!submission.value.contentEquals(transaction.expectedSignature)) {
                        return SignAndSendSubmissionResult.Fatal(
                            reason = SignAndSendFatalReason.SIGNATURE_MISMATCH,
                            submittedCount = submittedSignatures.count { it != null },
                        )
                    }
                    submittedSignatures[index] = submission.value.copyOf()

                    if (
                        options.waitForCommitmentToSendNextTransaction == true &&
                        index < transactions.lastIndex
                    ) {
                        if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled
                        val target = options.commitment ?: "confirmed"
                        when (
                            val confirmation = rpcGateway.awaitCommitment(
                                signature = submission.value,
                                commitment = target,
                            )
                        ) {
                            is DevnetRpcResult.Success -> {
                                if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled
                                if (!confirmation.value) {
                                    return SignAndSendSubmissionResult.Fatal(
                                        reason = SignAndSendFatalReason.COMMITMENT_NOT_REACHED_BEFORE_NEXT,
                                        submittedCount = submittedSignatures.count { it != null },
                                    )
                                }
                                if (options.commitment != null) {
                                    commitmentVerified[index] = true
                                }
                            }

                            is DevnetRpcResult.RpcError,
                            is DevnetRpcResult.TransportFailure,
                            DevnetRpcResult.MalformedResponse -> {
                                return SignAndSendSubmissionResult.Fatal(
                                    reason = SignAndSendFatalReason.COMMITMENT_UNAVAILABLE_BEFORE_NEXT,
                                    submittedCount = submittedSignatures.count { it != null },
                                )
                            }
                        }
                    }
                }

                is DevnetRpcResult.RpcError,
                is DevnetRpcResult.TransportFailure,
                DevnetRpcResult.MalformedResponse -> Unit
            }
        }

        if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled

        if (submittedSignatures.any { it == null }) {
            return SignAndSendSubmissionResult.NotSubmitted(
                signatures = submittedSignatures.map { it?.copyOf() },
            )
        }

        if (options.commitment != null) {
            for (index in submittedSignatures.indices) {
                if (commitmentVerified[index]) continue
                if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled
                val signature = checkNotNull(submittedSignatures[index])
                when (
                    val confirmation = rpcGateway.awaitCommitment(
                        signature = signature,
                        commitment = options.commitment,
                    )
                ) {
                    is DevnetRpcResult.Success -> {
                        if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled
                        if (!confirmation.value) {
                            return SignAndSendSubmissionResult.Fatal(
                                reason = SignAndSendFatalReason.COMMITMENT_NOT_REACHED,
                                submittedCount = submittedSignatures.size,
                            )
                        }
                    }

                    is DevnetRpcResult.RpcError,
                    is DevnetRpcResult.TransportFailure,
                    DevnetRpcResult.MalformedResponse -> {
                        return SignAndSendSubmissionResult.Fatal(
                            reason = SignAndSendFatalReason.COMMITMENT_UNAVAILABLE,
                            submittedCount = submittedSignatures.size,
                        )
                    }
                }
            }
        }

        if (!isRequestCurrent()) return SignAndSendSubmissionResult.Cancelled

        return SignAndSendSubmissionResult.Submitted(
            signatures = submittedSignatures.map { checkNotNull(it).copyOf() },
        )
    }
}
