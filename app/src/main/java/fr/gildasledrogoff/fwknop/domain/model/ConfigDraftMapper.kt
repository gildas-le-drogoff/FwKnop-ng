package fr.gildasledrogoff.fwknop.domain.model

fun SpaConfig.toDraft(): ConfigDraft = ConfigDraft(
    nickname = nickname.value,
    server = server.toDraft(),
    access = access.toDraft(),
    sourceIp = sourceIp.toDraft(),
    crypto = crypto.toDraft(),
    externalApp = externalApp.toDraft(),
)

private fun ServerEndpoint.toDraft(): ServerDraft = ServerDraft(
    host = host,
    port = (port as? ServerPort.Fixed)?.value?.toString() ?: ServerPort.DEFAULT.toString(),
    randomPort = port is ServerPort.Random,
    protocol = protocol,
)

private fun AccessRule.toDraft(): AccessDraft {
    val ports = portAccess?.toDraft() ?: AccessDraft(ports = "")
    return when (this) {
        is AccessRule.OpenPort -> ports
        is AccessRule.NatAccess -> ports.copy(
            type = MessageType.NAT_ACCESS,
            natIp = natIp,
            natPort = "$natPort"
        )

        is AccessRule.LocalNatAccess -> ports.copy(
            type = MessageType.LOCAL_NAT_ACCESS,
            natPort = "$natPort"
        )

        is AccessRule.ServerCommand -> ports.copy(
            type = MessageType.SERVER_COMMAND,
            command = command
        )
    }
}

private fun PortAccess.toDraft(): AccessDraft = AccessDraft(
    ports = ports,
    timeout = timeoutSeconds?.toString().orEmpty(),
    keepOpen = keepOpen,
)

private fun SourceIp.toDraft(): SourceIpDraft = when (this) {
    SourceIp.Resolve -> SourceIpDraft(SourceIpMode.RESOLVE)
    SourceIp.Source -> SourceIpDraft(SourceIpMode.SOURCE)
    SourceIp.Prompt -> SourceIpDraft(SourceIpMode.PROMPT)
    is SourceIp.Fixed -> SourceIpDraft(SourceIpMode.ALLOW, address)
}

private fun SpaCrypto.toDraft(): CryptoDraft {
    val base = CryptoDraft(
        hmac = hmac?.value.orEmpty(),
        hmacBase64 = hmac?.base64 ?: false,
        digest = digest,
        hmacDigest = hmacDigest,
        legacy = legacyMode,
    )
    return when (val enc = encryption) {
        is Encryption.Rijndael -> base.copy(
            key = enc.key.value,
            keyBase64 = enc.key.base64,
            gpgRecipient = enc.dormantRecipient,
            gpgSigner = enc.dormantSigner,
        )
        is Encryption.Gpg -> base.copy(
            useGpg = true,
            gpgRecipient = enc.recipient,
            gpgSigner = enc.signer,
            key = enc.dormantKey?.value.orEmpty(),
            keyBase64 = enc.dormantKey?.base64 ?: false,
        )
    }
}

private fun ExternalApp.toDraft(): ExternalAppDraft = when (this) {
    ExternalApp.None -> ExternalAppDraft()
    is ExternalApp.SshUri -> ExternalAppDraft(ExternalAppType.SSH_URI, sshTarget = target)
    is ExternalApp.OpenVpn -> ExternalAppDraft(ExternalAppType.OPENVPN, vpnProfile = profile)
    is ExternalApp.WireGuard ->
        ExternalAppDraft(ExternalAppType.WIREGUARD, wgTunnel = tunnel, wgRemoteKey = remoteKey)
}

fun CryptoDraft.withQrKeys(keys: QrKeys): CryptoDraft {
    val withKey =
        keys.key?.let { copy(useGpg = false, key = it.value, keyBase64 = it.base64) } ?: this
    return keys.hmac?.let { withKey.copy(hmac = it.value, hmacBase64 = it.base64) } ?: withKey
}

fun ConfigDraft.withQrConfig(qr: ParsedQr): ConfigDraft {
    val config = qr.config ?: return copy(crypto = crypto.withQrKeys(qr.keys))
    return copy(
        nickname = config.nickname ?: nickname,
        server = server.with(config),
        access = access.with(config),
        sourceIp = sourceIp.with(config),
        externalApp = externalApp.with(config),
        crypto = crypto.with(config).withQrKeys(qr.keys),
    )
}

private fun ServerDraft.with(config: QrConfig) = copy(
    host = config.serverHost ?: host,
    port = config.serverPort?.toString() ?: port,
    randomPort = config.randomPort ?: randomPort,
    protocol = config.protocol ?: protocol,
)

private fun AccessDraft.with(config: QrConfig): AccessDraft {
    val importedPorts =
        config.ports ?: if (config.messageType == MessageType.SERVER_COMMAND) "" else ports
    return copy(
        type = config.messageType ?: type,
        ports = importedPorts,
        timeout = config.timeoutSeconds?.toString() ?: timeout,
        keepOpen = config.keepOpen ?: keepOpen,
        natIp = config.natIp ?: natIp,
        natPort = config.natPort?.toString() ?: natPort,
        command = config.command ?: command,
    )
}

private fun SourceIpDraft.with(config: QrConfig) = copy(
    mode = config.sourceMode ?: mode,
    address = config.allowIp ?: address,
)

private fun ExternalAppDraft.with(config: QrConfig) = when {
    config.sshTarget != null -> copy(type = ExternalAppType.SSH_URI, sshTarget = config.sshTarget)
    config.vpnProfile != null -> copy(
        type = ExternalAppType.OPENVPN,
        vpnProfile = config.vpnProfile
    )

    config.wgTunnel != null -> copy(
        type = ExternalAppType.WIREGUARD,
        wgTunnel = config.wgTunnel,
        wgRemoteKey = config.wgRemoteKey ?: wgRemoteKey,
    )

    else -> this
}

private fun CryptoDraft.with(config: QrConfig) = copy(
    digest = config.digest ?: digest,
    hmacDigest = config.hmacDigest ?: hmacDigest,
    legacy = config.legacy ?: legacy,
    gpgRecipient = config.gpgRecipient ?: gpgRecipient,
    gpgSigner = config.gpgSigner ?: gpgSigner,
    useGpg = config.gpgRecipient != null || config.gpgSigner != null || useGpg,
)
