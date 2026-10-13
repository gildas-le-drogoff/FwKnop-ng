package fr.gildasledrogoff.fwknop.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = ConfigColumns.TABLE,
    indices = [Index(value = [ConfigColumns.NICK_NAME], unique = true)]
)
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = ConfigColumns.NICK_NAME) val nickName: String,
    @ColumnInfo(name = ConfigColumns.ACCESS_IP) val accessIp: String,
    @ColumnInfo(name = ConfigColumns.PORTS) val ports: String,
    @ColumnInfo(name = ConfigColumns.SERVER_IP) val serverIp: String,
    @ColumnInfo(name = ConfigColumns.SERVER_PORT) val serverPort: String,
    @ColumnInfo(name = ConfigColumns.SERVER_TIMEOUT) val serverTimeout: String,
    @ColumnInfo(name = ConfigColumns.KEY) val key: String,
    @ColumnInfo(name = ConfigColumns.KEY_BASE64) val keyBase64: Boolean,
    @ColumnInfo(name = ConfigColumns.HMAC) val hmac: String,
    @ColumnInfo(name = ConfigColumns.NAT_IP) val natIp: String,
    @ColumnInfo(name = ConfigColumns.NAT_PORT) val natPort: String,
    @ColumnInfo(name = ConfigColumns.SERVER_CMD) val serverCmd: String,
    @ColumnInfo(name = ConfigColumns.HMAC_BASE64) val hmacBase64: Boolean,
    @ColumnInfo(name = ConfigColumns.SSH_CMD) val sshCmd: String,
    @ColumnInfo(name = ConfigColumns.LEGACY) val legacy: Boolean,
    @ColumnInfo(name = ConfigColumns.PROTOCOL) val protocol: String,
    @ColumnInfo(name = ConfigColumns.DIGEST_TYPE) val digestType: String,
    @ColumnInfo(name = ConfigColumns.HMAC_TYPE) val hmacType: String,
    @ColumnInfo(name = ConfigColumns.KEEP_OPEN) val keepOpen: Boolean,
    @ColumnInfo(name = ConfigColumns.GPG_SIG) val gpgSig: Long,
    @ColumnInfo(name = ConfigColumns.GPG_KEY) val gpgKey: Long,
    @ColumnInfo(name = ConfigColumns.USE_GPG, defaultValue = "0") val useGpg: Boolean,
)

object ConfigColumns {
    const val TABLE = "configs"
    const val NICK_NAME = "NICK_NAME"
    const val ACCESS_IP = "ACCESS_IP"
    const val PORTS = "PORTS"
    const val SERVER_IP = "SERVER_IP"
    const val SERVER_PORT = "SERVER_PORT"
    const val SERVER_TIMEOUT = "SERVER_TIMEOUT"
    const val KEY = "KEY"
    const val KEY_BASE64 = "KEY_BASE64"
    const val HMAC = "HMAC"
    const val NAT_IP = "NAT_IP"
    const val NAT_PORT = "NAT_PORT"
    const val SERVER_CMD = "SERVER_CMD"
    const val HMAC_BASE64 = "HMAC_BASE64"
    const val SSH_CMD = "SSH_CMD"
    const val LEGACY = "LEGACY"
    const val PROTOCOL = "PROTOCOL"
    const val DIGEST_TYPE = "DIGEST_TYPE"
    const val HMAC_TYPE = "HMAC_TYPE"
    const val KEEP_OPEN = "KEEP_OPEN"
    const val GPG_SIG = "GPG_SIG"
    const val GPG_KEY = "GPG_KEY"
    const val USE_GPG = "USE_GPG"
}
