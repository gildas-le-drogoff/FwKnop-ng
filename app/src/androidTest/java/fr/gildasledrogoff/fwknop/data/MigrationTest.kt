// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.database.*
import org.junit.Rule
import org.junit.Test
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
    @Test
    fun v3ConfigsStayInSpaMode() {
        helper.createDatabase(DB, 3).use { db -> insertV1(db, "plain", gpgKey = 0) }
        helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT KNOCK_SEQUENCE, KNOCK_DELAY FROM configs").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEmpty()
                assertThat(cursor.getInt(1)).isEqualTo(500)
            }
        }
    }
    @Test
    fun v4ConfigsHaveNoWakeOnLan() {
        helper.createDatabase(DB, 4).use { db -> insertV1(db, "plain", gpgKey = 0) }
        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT WAKE_ON_LAN FROM configs").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEmpty()
            }
        }
    }
    @Test
    fun v5KnockLogEntriesAreKnocks() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL("INSERT INTO knock_log (TIMESTAMP, NICK_NAME, PROTOCOL, RESULT) VALUES (0, 'home', 'UDP', 'SENT')")
        }
        helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT KIND FROM knock_log").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("KNOCK")
            }
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
