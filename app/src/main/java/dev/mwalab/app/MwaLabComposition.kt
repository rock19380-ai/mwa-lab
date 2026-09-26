package dev.mwalab.app

import android.content.Context
import dev.mwalab.identity.AndroidKeystoreIdentityRepository
import dev.mwalab.identity.IdentityRepository

object MwaLabComposition {
    @Volatile
    private var identityRepositoryInstance: IdentityRepository? = null

    fun identityRepository(context: Context): IdentityRepository =
        identityRepositoryInstance ?: synchronized(this) {
            identityRepositoryInstance ?: AndroidKeystoreIdentityRepository(
                context.applicationContext,
            ).also { identityRepositoryInstance = it }
        }
}
