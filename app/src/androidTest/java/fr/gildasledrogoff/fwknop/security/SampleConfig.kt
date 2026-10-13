package fr.gildasledrogoff.fwknop.security
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.DigestAlgorithm
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ServerEndpoint
import fr.gildasledrogoff.fwknop.domain.model.ServerPort
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SpaConfig
import fr.gildasledrogoff.fwknop.domain.model.SpaCrypto
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
