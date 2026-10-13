package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.VaultStatus
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveVaultStatusUseCase @Inject constructor(private val vault: VaultRepository) {
    operator fun invoke(): StateFlow<VaultStatus> = vault.status
}

class UnlockVaultUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(): DomainResult<Unit, VaultError> = vault.unlock()
}

class LockVaultUseCase @Inject constructor(private val vault: VaultRepository) {
    operator fun invoke() = vault.lock()
}

class SetAppLockUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(enabled: Boolean): DomainResult<Unit, VaultError> {
        if (enabled && !vault.isDeviceSecure()) return DomainResult.Failure(VaultError.DeviceNotSecure)
        return vault.setLockEnabled(enabled)
    }
}

class CanEnableAppLockUseCase @Inject constructor(private val vault: VaultRepository) {
    operator fun invoke(): Boolean = vault.isDeviceSecure()
}

class ResetVaultUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(): DomainResult<Unit, VaultError> = vault.reset()
}

class RotateVaultKeysUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(): DomainResult<Unit, VaultError> = vault.rotateKeys()
}
