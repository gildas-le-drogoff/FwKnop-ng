package fr.gildasledrogoff.fwknop.fakes
import android.app.Activity
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagDigester
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagKeys
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.error.VaultError
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.DigestAlgorithm
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyRole
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEntry
import fr.gildasledrogoff.fwknop.domain.model.KnockLogEvent
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.KnockTimerPlan
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import fr.gildasledrogoff.fwknop.domain.model.NfcTagKey
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ServerEndpoint
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.SpaCrypto
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams
import fr.gildasledrogoff.fwknop.domain.model.VaultState
import fr.gildasledrogoff.fwknop.domain.model.VaultStatus
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigResult
import fr.gildasledrogoff.fwknop.domain.repository.GpgRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockTimerScheduler
import fr.gildasledrogoff.fwknop.domain.repository.NfcTagRepository
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionSnapshot
import fr.gildasledrogoff.fwknop.utils.helpers.PermissionStatus
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Base64
fun sampleConfig(
    nickname: String = "home",
    access: AccessRule = AccessRule.OpenPort(PortAccess("tcp/22", 30, keepOpen = false)),
    sourceIp: SourceIp = SourceIp.Fixed("198.51.100.4"),
    encryption: Encryption = Encryption.Rijndael(SecretKey("secret", base64 = false)),
) = SpaConfig(
    nickname = Nickname(nickname),
    server = ServerEndpoint("knock.example.org", ServerPort.Fixed(62201), Protocol.UDP),
    access = access,
    sourceIp = sourceIp,
    crypto = SpaCrypto(encryption, SecretKey("hmac", false), DigestAlgorithm.SHA256, DigestAlgorithm.SHA256, false),
    externalApp = ExternalApp.None,
)
class FakeConfigRepository(initial: List<SpaConfig> = emptyList()) : ConfigRepository {
    val configs = MutableStateFlow(initial)
    var storageError: Throwable? = null
    override fun observeConfigs(): Flow<ConfigResult<List<SpaConfig>>> = configs.map { guarded { it } }
    override suspend fun getConfig(nickname: Nickname): ConfigResult<SpaConfig> =
        storageError?.let { DomainResult.Failure(ConfigError.Storage(it)) }
            ?: configs.value.firstOrNull { it.nickname == nickname }?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(ConfigError.NotFound)
    override suspend fun exists(nickname: Nickname) = guarded { configs.value.any { it.nickname == nickname } }
    override suspend fun saveConfig(config: SpaConfig, previous: Nickname?) = guarded {
        configs.value = configs.value.filterNot { it.nickname == previous || it.nickname == config.nickname } + config
    }
    override suspend fun deleteConfig(nickname: Nickname) = guarded {
        configs.value = configs.value.filterNot { it.nickname == nickname }
    }
    override suspend fun getAll() = guarded { configs.value }
    override suspend fun replaceAll(configs: List<SpaConfig>) = guarded {
        this.configs.value = configs
    }
    override suspend fun saveAll(configs: List<SpaConfig>) = guarded {
        val nicknames = configs.map { it.nickname }.toSet()
        this.configs.value = this.configs.value.filterNot { it.nickname in nicknames } + configs
    }
    private inline fun <T> guarded(block: () -> T): ConfigResult<T> =
        storageError?.let { DomainResult.Failure(ConfigError.Storage(it)) } ?: DomainResult.Success(block())
}

class FakeNfcTagRepository(var key: ByteArray? = null) : NfcTagRepository {
    override suspend fun digest(nickname: Nickname): NfcTagDigest {
        val key = key ?: ByteArray(NfcTagKeys.KEY_BYTES) { it.toByte() }.also { key = it }
        return NfcTagDigest(NfcTagDigester.digest(key, nickname.value))
    }
    override suspend fun match(digests: List<NfcTagDigest>, candidates: List<Nickname>): List<Nickname?> =
        digests.map { digest ->
            key?.let { key -> candidates.firstOrNull { NfcTagDigester.matches(key, digest.bytes, it.value) } }
        }
    override suspend fun exportKey(): NfcTagKey? = key?.let { NfcTagKey(Base64.getEncoder().encodeToString(it)) }
    override suspend fun importKey(key: NfcTagKey) {
        this.key = Base64.getDecoder().decode(key.base64)
    }
}
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun saveSettings(settings: AppSettings) {
        state.value = settings
    }
    override suspend fun markHelpShown() {
        state.value = state.value.copy(helpShown = true)
    }
    override suspend fun dismissLockSuggestion() {
        state.value = state.value.copy(lockSuggestionDismissed = true)
    }
}
class FakePublicIpRepository(
    var results: Map<String, DomainResult<ResolvedSourceIp, KnockError>> = emptyMap(),
    var default: DomainResult<ResolvedSourceIp, KnockError> =
        DomainResult.Success(ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example"))),
) : PublicIpRepository {
    val asked = mutableListOf<String>()
    override suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError> {
        asked += serverIp
        return results[serverIp] ?: default
    }
}
class FakeSpaPacketRepository : SpaPacketRepository {
    val built = mutableListOf<SpaPacketParams>()
    val finalizedCiphers = mutableListOf<String>()
    override suspend fun buildPacket(params: SpaPacketParams): DomainResult<String, KnockError> {
        built += params
        return DomainResult.Success("packet-${built.size}")
    }
    override suspend fun buildGpgPayload(params: SpaPacketParams): DomainResult<String, KnockError> {
        built += params
        return DomainResult.Success("plaintext")
    }
    override suspend fun finalizeGpgPacket(params: SpaPacketParams, cipher: String): DomainResult<String, KnockError> {
        finalizedCiphers += cipher
        return DomainResult.Success("gpg-packet")
    }
}
class FakeSpaTransportRepository(
    private val serverIp: String = "192.0.2.10",
    var serverFamilies: List<String> = listOf(serverIp),
) : SpaTransportRepository {
    val sent = mutableListOf<Pair<String, KnockTarget>>()
    var sendError: KnockError? = null
    var localNetworkGranted = true
    var serverOnLocalNetwork = false
    override suspend fun resolveHost(host: String, sourceIp: String): DomainResult<String, KnockError> =
        DomainResult.Success(serverIp)
    override suspend fun resolveHostFamilies(host: String): DomainResult<List<String>, KnockError> =
        DomainResult.Success(serverFamilies)
    override suspend fun sendPacket(packet: String, target: KnockTarget): DomainResult<Unit, KnockError> {
        sendError?.let { return DomainResult.Failure(it) }
        if (serverOnLocalNetwork && !localNetworkGranted) {
            return DomainResult.Failure(KnockError.LocalNetworkPermissionRequired)
        }
        sent += packet to target
        return DomainResult.Success(Unit)
    }
}
class FakeGpgRepository(var encryptResult: DomainResult<String, GpgError> = DomainResult.Success("cipher")) :
    GpgRepository {
    val encryptCalls = mutableListOf<Pair<String, InteractionId?>>()
    override suspend fun encrypt(
        plaintext: String,
        keys: Encryption.Gpg,
        resume: InteractionId?,
    ): DomainResult<String, GpgError> {
        encryptCalls += plaintext to resume
        return encryptResult
    }
    override suspend fun selectKey(role: GpgKeyRole, resume: InteractionId?): DomainResult<GpgKeyId, GpgError> =
        DomainResult.Success(GpgKeyId(42L))
}
class FakeKnockTimerScheduler(
    var notificationsAllowed: Boolean = true,
    var keepOpenAllowed: Boolean = true,
) : KnockTimerScheduler {
    val plans = mutableListOf<KnockTimerPlan>()
    override fun canNotify() = notificationsAllowed
    override fun schedule(plan: KnockTimerPlan): Boolean {
        if (plan is KnockTimerPlan.KeepOpen && !keepOpenAllowed) return false
        plans += plan
        return true
    }
}
class FakeVaultRepository(
    lockEnabled: Boolean = false,
    var unlockResult: DomainResult<Unit, VaultError> = DomainResult.Success(Unit),
    var deviceSecure: Boolean = true,
) : VaultRepository {
    val statusFlow = MutableStateFlow(VaultStatus(VaultState.LOCKED, lockEnabled))
    override val status: StateFlow<VaultStatus> = statusFlow
    var unlockCalls = 0
        private set
    override suspend fun unlock(): DomainResult<Unit, VaultError> {
        unlockCalls++
        if (unlockResult is DomainResult.Success) statusFlow.value = statusFlow.value.copy(state = VaultState.UNLOCKED)
        return unlockResult
    }
    override fun lock() {
        statusFlow.value = statusFlow.value.copy(state = VaultState.LOCKED)
    }
    override suspend fun setLockEnabled(enabled: Boolean): DomainResult<Unit, VaultError> {
        statusFlow.value = statusFlow.value.copy(lockEnabled = enabled)
        return DomainResult.Success(Unit)
    }
    var rotations = 0
        private set
    override suspend fun rotateKeys(): DomainResult<Unit, VaultError> {
        rotations++
        return DomainResult.Success(Unit)
    }
    override suspend fun reset(): DomainResult<Unit, VaultError> {
        statusFlow.value = VaultStatus(VaultState.LOCKED, lockEnabled = false)
        return DomainResult.Success(Unit)
    }
    override fun isDeviceSecure() = deviceSecure
}
class FakeRuntimePermissions(
    notifications: PermissionStatus = PermissionStatus.GRANTED,
    localNetwork: PermissionStatus = PermissionStatus.GRANTED,
) : RuntimePermissions {
    val snapshotFlow = MutableStateFlow(PermissionSnapshot(notifications = notifications, localNetwork = localNetwork))
    override val snapshot: StateFlow<PermissionSnapshot> = snapshotFlow
    var refreshes = 0
        private set
    val prompted = mutableSetOf<String>()
    override fun refresh(activity: Activity?) {
        refreshes++
    }
    override fun onPromptShown(permission: String) {
        prompted += permission
        snapshotFlow.value = snapshotFlow.value.copy(prompted = prompted.toSet())
    }
    override fun onPromptResult(permission: String, granted: Boolean, permanentlyDenied: Boolean) {
        prompted += permission
        val status = when {
            granted -> PermissionStatus.GRANTED
            permanentlyDenied -> PermissionStatus.DENIED_PERMANENTLY
            else -> PermissionStatus.DENIED
        }
        val current = snapshotFlow.value
        snapshotFlow.value = current.copy(
            prompted = prompted.toSet(),
            localNetwork = if (permission == LocalNetworkAccess.PERMISSION) status else current.localNetwork,
            notifications = if (permission == RuntimePermissions.NOTIFICATIONS) status else current.notifications,
        )
    }
}
class FakeKnockHistoryRepository : KnockHistoryRepository {
    val recorded = mutableListOf<KnockLogEvent>()
    var purges = 0
        private set
    override fun observeHistory(): Flow<List<KnockLogEntry>> = MutableStateFlow(
        recorded.mapIndexed { i, event -> KnockLogEntry(i.toLong(), Instant.EPOCH, event) }.reversed()
    )
    override suspend fun record(event: KnockLogEvent) {
        recorded += event
    }
    override suspend fun purgeExpired() {
        purges++
    }
    override suspend fun clear() = recorded.clear()
}
