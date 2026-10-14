// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Immutable

enum class BackupMode {
    EXPORT_ALL, EXPORT_CONFIG, IMPORT;

    val isExport: Boolean get() = this != IMPORT
}

@Immutable
sealed interface BackupDialog {
    data object None : BackupDialog
    data class Passphrase(val mode: BackupMode, val wrongPassphrase: Boolean = false) : BackupDialog
    data class ConfirmRestore(val configCount: Int) : BackupDialog
    data class RenameImport(val nickname: String, val suggestion: String, val taken: Set<String>) :
        BackupDialog
}

@Immutable
data class BackupState(
    val dialog: BackupDialog = BackupDialog.None,
    val busy: Boolean = false,
    val error: UiError? = null,
)

sealed interface BackupEvent {
    data object ExportAllRequested : BackupEvent
    data class ExportConfigRequested(val nickname: String) : BackupEvent
    data object ImportRequested : BackupEvent
    data object RestoreConfirmed : BackupEvent
    data class ImportRenamed(val nickname: String) : BackupEvent
    data class DocumentPicked(val uri: Uri?) : BackupEvent
    data class AuthCompleted(val result: AuthResult) : BackupEvent
    class PassphraseEntered(val passphrase: CharArray) : BackupEvent
    data object DialogDismissed : BackupEvent
    data object DismissError : BackupEvent
}

sealed interface BackupEffect {
    data class PickExportTarget(val fileName: String) : BackupEffect
    data object PickImportSource : BackupEffect
    data class Authenticate(val purpose: AuthPurpose) : BackupEffect
    data class Done(@param:PluralsRes val resId: Int, val count: Int) : BackupEffect
    data object Restored : BackupEffect
}
