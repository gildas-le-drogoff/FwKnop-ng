package fr.gildasledrogoff.fwknop.data.security
import fr.gildasledrogoff.fwknop.data.local.security.ColumnCipher
import fr.gildasledrogoff.fwknop.data.local.security.ConfigEntityCipher
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.fakes.newKeyset
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test
class ConfigEntityCipherTest {
    private val cipher = ConfigEntityCipher(ColumnCipher(newKeyset().aead()))
    private val entity = sampleConfig().toEntity().copy(id = 7, natIp = "10.0.0.2", serverCmd = "systemctl start x")
    @Test
    fun `seal then open gives the row back`() {
        assertThat(cipher.open(cipher.seal(entity))).isEqualTo(entity)
    }
    @Test
    fun `keys and infrastructure are sealed, the lookup key and flags stay clear`() {
        val sealed = cipher.seal(entity)
        val clear = listOf(entity.key, entity.hmac, entity.serverIp, entity.serverPort, entity.ports, entity.accessIp,
            entity.natIp, entity.serverCmd)
        val stored = listOf(sealed.key, sealed.hmac, sealed.serverIp, sealed.serverPort, sealed.ports, sealed.accessIp,
            sealed.natIp, sealed.serverCmd)
        stored.zip(clear).forEach { (s, c) -> assertThat(s).isNotEqualTo(c) }
        assertThat(stored.joinToString()).doesNotContain("secret")
        assertThat(stored.joinToString()).doesNotContain("knock.example.org")
        assertThat(sealed.copy(
            accessIp = entity.accessIp, ports = entity.ports, serverIp = entity.serverIp,
            serverPort = entity.serverPort, serverTimeout = entity.serverTimeout, key = entity.key, hmac = entity.hmac,
            natIp = entity.natIp, natPort = entity.natPort, serverCmd = entity.serverCmd, sshCmd = entity.sshCmd,
        )).isEqualTo(entity)
    }
    @Test
    fun `every column declared sensitive is actually sealed`() {
        assertThat(ConfigEntityCipher.SENSITIVE).hasSize(11)
        assertThat(ConfigEntityCipher.SENSITIVE).containsAtLeast("KEY", "HMAC", "SERVER_IP", "ACCESS_IP", "NAT_IP")
        assertThat(ConfigEntityCipher.SENSITIVE).doesNotContain("NICK_NAME")
    }
}
