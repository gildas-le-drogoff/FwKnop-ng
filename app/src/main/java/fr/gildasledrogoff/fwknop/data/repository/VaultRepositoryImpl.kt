package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFactory
import fr.gildasledrogoff.fwknop.data.local.security.ColumnCipher
import fr.gildasledrogoff.fwknop.data.local.security.DatabasePassphrases
import fr.gildasledrogoff.fwknop.data.local.security.DeviceCredential
import fr.gildasledrogoff.fwknop.data.local.security.KeysetRotation
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyRewrap
import fr.gildasledrogoff.fwknop.data.local.security.OpenVault
import fr.gildasledrogoff.fwknop.data.local.security.VaultFailures
import fr.gildasledrogoff.fwknop.data.local.security.VaultMetadataStore
import fr.gildasledrogoff.fwknop.data.local.security.VaultOpener
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.local.security.VaultWiper
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.model.VaultStatus
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val opener: VaultOpener,
    private val databases: DatabaseFactory,
    private val passphrases: DatabasePassphrases,
    private val rewrap: MasterKeyRewrap,
    private val rotation: KeysetRotation,
    private val wiper: VaultWiper,
    private val metadata: VaultMetadataStore,
    private val session: VaultSession,
    private val device: DeviceCredential,
    private val dispatchers: DispatcherProvider,
) : VaultRepository {
    private val _status = MutableStateFlow(VaultStatus(VaultState.LOCKED, initialLock()))
    override val status: StateFlow<VaultStatus> = _status.asStateFlow()
    override suspend fun unlock() = guarded {
        if (session.open.value == null) session.open(openVault())
        _status.update { it.copy(state = VaultState.UNLOCKED) }
    }

    override fun lock() {
        session.close()
        _status.update { it.copy(state = VaultState.LOCKED) }
    }

    override suspend fun setLockEnabled(enabled: Boolean) = guarded {
        rewrap.rewrap(requireOpen().keyset, enabled)
        _status.update { it.copy(lockEnabled = enabled) }
    }

    override suspend fun rotateKeys() = guarded {
        val alias = checkNotNull(metadata.read().masterKeyAlias) { "no vault" }
        session.writing { vault -> session.replaceKeyset(rotation.rotate(vault, alias)) }
    }

    override suspend fun reset() = guarded {
        session.close()
        wiper.wipe()
        _status.value = VaultStatus(VaultState.LOCKED, initialLock())
    }

    override fun isDeviceSecure(): Boolean = device.isSecure()

    
    private fun initialLock(): Boolean = metadata.read().lockEnabled

    private fun openVault(): OpenVault {
        val keyset = opener.open(lockNew = _status.value.lockEnabled)
        val aead = keyset.aead()
        return OpenVault(keyset, ColumnCipher(aead), databases.open(passphrases.obtain(aead)))
    }

    private fun requireOpen(): OpenVault = checkNotNull(session.open.value) { "vault locked" }
    private suspend fun guarded(block: suspend () -> Unit): DomainResult<Unit, VaultError> =
        withContext(dispatchers.io) {
            session.mutex.withLock {
                try {
                    block()
                    DomainResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    DebugLog.failure("vault operation", e)
                    DomainResult.Failure(VaultFailures.classify(e))
                }
            }
        }
}
