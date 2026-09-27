package dev.mwalab.app

import android.content.Context
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.identity.AndroidKeystoreIdentityRepository
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.signing.LabSigningService

object MwaLabComposition {
    @Volatile
    private var identityServiceInstance: AndroidKeystoreIdentityRepository? = null

    @Volatile
    private var approvalCoordinatorInstance: ApprovalCoordinator? = null

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
}
