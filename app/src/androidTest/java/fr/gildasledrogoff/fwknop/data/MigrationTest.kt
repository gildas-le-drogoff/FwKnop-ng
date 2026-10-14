package fr.gildasledrogoff.fwknop.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.database.FwknopDatabase
import fr.gildasledrogoff.fwknop.data.local.database.MIGRATION_1_2
import fr.gildasledrogoff.fwknop.data.local.database.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FwknopDatabase::class.java)

    @Test
    fun v1RowsGetTheirGpgModeFromTheRecipient() {
        helper.createDatabase(DB, 1).use { db ->
            insertV1(db, "plain", gpgKey = 0)
            insertV1(db, "gpg", gpgKey = 0x1234)
        }
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT NICK_NAME, USE_GPG FROM configs ORDER BY NICK_NAME").use { cursor ->
                val modes = buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
                }
                assertThat(modes).containsExactly("gpg", 1, "plain", 0)
            }
        }
    }

    @Test
    fun v2ConfigsSurviveTheKnockLogTableCreation() {
        helper.createDatabase(DB, 2).use { db -> insertV1(db, "plain", gpgKey = 0) }
        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM configs").use { assertThat(it.moveToFirst() && it.getInt(0) == 1).isTrue() }
            db.query("SELECT COUNT(*) FROM knock_log").use { assertThat(it.moveToFirst() && it.getInt(0) == 0).isTrue() }
        }
    }

    private fun insertV1(db: SupportSQLiteDatabase, nickname: String, gpgKey: Long) =
        db.execSQL(
            """
            INSERT INTO configs (NICK_NAME, ACCESS_IP, PORTS, SERVER_IP, SERVER_PORT, SERVER_TIMEOUT, KEY,
                KEY_BASE64, HMAC, NAT_IP, NAT_PORT, SERVER_CMD, HMAC_BASE64, SSH_CMD, LEGACY, PROTOCOL,
                DIGEST_TYPE, HMAC_TYPE, KEEP_OPEN, GPG_SIG, GPG_KEY)
            VALUES (?, '', '', '', '62201', '', 'secret', 0, '', '', '', '', 0, '', 0, 'udp',
                'SHA256', 'SHA256', 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(nickname, gpgKey),
        )

    private companion object {
        const val DB = "migration-test.db"
    }
}
