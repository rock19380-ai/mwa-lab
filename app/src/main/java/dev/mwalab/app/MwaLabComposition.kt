package dev.mwalab.app

import android.content.Context
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.identity.AndroidKeystoreIdentityRepository
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.signing.LabSigningService
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.SolanaDevnetRpcGateway

object MwaLabComposition {
    @Volatile
    private var identityServiceInstance: AndroidKeystoreIdentityRepository? = null

    @Volatile
    private var approvalCoordinatorInstance: ApprovalCoordinator? = null

    @Volatile
    private var devnetRpcGatewayInstance: DevnetRpcGateway? = null

    private fun identityService(context: Context): AndroidKeystoreIdentityRepository =
        identityServiceInstance ?: synchronized(this) {
            identityServiceInstance ?: AndroidKeystoreIdentityRepository(
                context.applicationContext,
            ).also { identityServiceInstance = it }
        }

    fun identityRepository(context: Context): IdentityRepository = identityService(context)

    fun signingService(context: Context): LabSigningService = identityService(context)

    fun approvalCoordinator(): ApprovalCoordinator =
        approvalCoordinatorInstance ?: synchronized(this) {
            approvalCoordinatorInstance ?: ApprovalCoordinator().also {
                approvalCoordinatorInstance = it
            }
        }

    fun devnetRpcGateway(): DevnetRpcGateway =
        devnetRpcGatewayInstance ?: synchronized(this) {
            devnetRpcGatewayInstance ?: SolanaDevnetRpcGateway().also {
                devnetRpcGatewayInstance = it
            }
        }
}
