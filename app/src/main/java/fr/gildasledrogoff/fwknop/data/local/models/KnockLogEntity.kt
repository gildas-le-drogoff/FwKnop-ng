package fr.gildasledrogoff.fwknop.data.local.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** No foreign key to configs: the journal outlives renamed or deleted configurations. */
@Entity(
    tableName = KnockLogColumns.TABLE,
    indices = [Index(value = [KnockLogColumns.TIMESTAMP])]
)
data class KnockLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** Epoch milliseconds (UTC). */
    @ColumnInfo(name = KnockLogColumns.TIMESTAMP) val timestamp: Long,
    @ColumnInfo(name = KnockLogColumns.NICK_NAME) val nickName: String,
    @ColumnInfo(name = KnockLogColumns.PROTOCOL) val protocol: String,
    @ColumnInfo(name = KnockLogColumns.SOURCE_IP) val sourceIp: String?,
    @ColumnInfo(name = KnockLogColumns.SOURCE_IP_METHOD) val sourceIpMethod: String?,
    /** STUN server or HTTPS host. */
    @ColumnInfo(name = KnockLogColumns.SOURCE_IP_METHOD_DETAIL) val sourceIpMethodDetail: String?,
    /** [KnockLogColumns.RESULT_SENT] or a KnockLogOutcome.Failed code. */
    @ColumnInfo(name = KnockLogColumns.RESULT) val result: String,
    @ColumnInfo(name = KnockLogColumns.ERROR_DETAIL) val errorDetail: String?,
)

object KnockLogColumns {
    const val TABLE = "knock_log"
    const val TIMESTAMP = "TIMESTAMP"
    const val NICK_NAME = "NICK_NAME"
    const val PROTOCOL = "PROTOCOL"
    const val SOURCE_IP = "SOURCE_IP"
    const val SOURCE_IP_METHOD = "SOURCE_IP_METHOD"
    const val SOURCE_IP_METHOD_DETAIL = "SOURCE_IP_METHOD_DETAIL"
    const val RESULT = "RESULT"
    const val ERROR_DETAIL = "ERROR_DETAIL"
    const val RESULT_SENT = "SENT"
}
