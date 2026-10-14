// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.security
import fr.gildasledrogoff.fwknop.domain.model.*

object SampleConfig {
    const val NICKNAME = "home"
    const val KEY = "RijndaelS3cr3tValue"
    const val HMAC = "HmacS3cr3tValue"
    const val SERVER = "knock.example.org"
    val CONFIG = SpaConfig(
        nickname = Nickname(NICKNAME),
        server = ServerEndpoint(SERVER, ServerPort.Fixed(ServerPort.DEFAULT), Protocol.UDP),
        access = AccessRule.OpenPort(PortAccess("tcp/22", 30, keepOpen = false)),
        sourceIp = SourceIp.Fixed("198.51.100.4"),
        crypto = SpaCrypto(
            Encryption.Rijndael(SecretKey(KEY, base64 = false)),
            SecretKey(HMAC, false),
            DigestAlgorithm.SHA256,
            DigestAlgorithm.SHA256,
            false,
        ),
        externalApp = ExternalApp.None,
    )
    val SECRETS = listOf(KEY, HMAC, SERVER)
}
