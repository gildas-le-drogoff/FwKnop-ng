package fr.gildasledrogoff.fwknop.data.backup

import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagKeys
import fr.gildasledrogoff.fwknop.data.mapper.LegacyValues
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.AutoLockDelay
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.NfcTagKey
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
internal data class BackupPayload(
    
    val settings: BackupSettings? = null,
    val configs: List<BackupConfig> = emptyList(),
    
    val nfcTagKey: String? = null,
)

@Serializable
internal data class BackupSettings(
    val dnsLookupEnabled: Boolean = AppSettings().dnsLookupEnabled,
    val stunLookupEnabled: Boolean = AppSettings().stunLookupEnabled,
    val nfcKnockEnabled: Boolean = AppSettings().nfcKnockEnabled,
    val notificationsEnabled: Boolean = AppSettings().notificationsEnabled,
    val ipLookupUrl: String = AppSettings().ipLookupUrl,
    val autoLockDelay: String = AppSettings().autoLockDelay.name,
)

@Serializable
internal data class BackupConfig(
    val nickName: String,
    val accessIp: String = LegacyValues.RESOLVE_IP,
    val ports: String = "",
    val serverIp: String = "",
    val serverPort: String = ServerPort.DEFAULT.toString(),
    val serverTimeout: String = "",
    val key: String = "",
    val keyBase64: Boolean = false,
    val hmac: String = "",
    val natIp: String = "",
    val natPort: String = "",
    val serverCmd: String = "",
    val hmacBase64: Boolean = false,
    val sshCmd: String = "",
    val legacy: Boolean = false,
    val protocol: String = "udp",
    val digestType: String = "SHA256",
    val hmacType: String = "SHA256",
    val keepOpen: Boolean = false,
    val gpgSig: Long = 0,
    val gpgKey: Long = 0,
    
    val useGpg: Boolean? = null,
)

internal fun Backup.toPayload() = BackupPayload(
    settings = settings?.let {
        BackupSettings(
            dnsLookupEnabled = it.dnsLookupEnabled,
            stunLookupEnabled = it.stunLookupEnabled,
            nfcKnockEnabled = it.nfcKnockEnabled,
            notificationsEnabled = it.notificationsEnabled,
            ipLookupUrl = it.ipLookupUrl,
            autoLockDelay = it.autoLockDelay.name,
        )
    },
    configs = configs.map { it.toEntity().toBackupConfig() },
    nfcTagKey = nfcTagKey?.base64,
)

internal fun BackupPayload.toDomain() = Backup(
    settings = settings?.let {
        AppSettings(
            dnsLookupEnabled = it.dnsLookupEnabled,
            stunLookupEnabled = it.stunLookupEnabled,
            nfcKnockEnabled = it.nfcKnockEnabled,
            notificationsEnabled = it.notificationsEnabled,
            ipLookupUrl = it.ipLookupUrl,
            autoLockDelay = AutoLockDelay.entries.firstOrNull { delay -> delay.name == it.autoLockDelay }
                ?: AppSettings().autoLockDelay,
        )
    },
    configs = configs.filter { it.nickName.isNotBlank() }.associateBy { it.nickName }.values
        .map { it.toEntity().toDomain() },
    nfcTagKey = nfcTagKey?.takeIf(::isValidNfcTagKey)?.let(::NfcTagKey),
)

private fun isValidNfcTagKey(base64: String): Boolean =
    runCatching { Base64.getDecoder().decode(base64).size == NfcTagKeys.KEY_BYTES }.getOrDefault(false)

private fun ConfigEntity.toBackupConfig() = BackupConfig(
    nickName = nickName,
    accessIp = accessIp,
    ports = ports,
    serverIp = serverIp,
    serverPort = serverPort,
    serverTimeout = serverTimeout,
    key = key,
    keyBase64 = keyBase64,
    hmac = hmac,
    natIp = natIp,
    natPort = natPort,
    serverCmd = serverCmd,
    hmacBase64 = hmacBase64,
    sshCmd = sshCmd,
    legacy = legacy,
    protocol = protocol,
    digestType = digestType,
    hmacType = hmacType,
    keepOpen = keepOpen,
    gpgSig = gpgSig,
    gpgKey = gpgKey,
    useGpg = useGpg,
)

private fun BackupConfig.toEntity() = ConfigEntity(
    nickName = nickName,
    accessIp = accessIp,
    ports = ports,
    serverIp = serverIp,
    serverPort = serverPort,
    serverTimeout = serverTimeout,
    key = key,
    keyBase64 = keyBase64,
    hmac = hmac,
    natIp = natIp,
    natPort = natPort,
    serverCmd = serverCmd,
    hmacBase64 = hmacBase64,
    sshCmd = sshCmd,
    legacy = legacy,
    protocol = protocol,
    digestType = digestType,
    hmacType = hmacType,
    keepOpen = keepOpen,
    gpgSig = gpgSig,
    gpgKey = gpgKey,
    useGpg = useGpg ?: (gpgKey != 0L),
)
