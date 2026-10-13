package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
sealed interface QrExportState {
    data object AwaitingAuth : QrExportState
    data object Denied : QrExportState
    data object Loading : QrExportState

    
    data class Ready(
        val nickname: String,
        val qr: ImageBitmap?,
        val page: Int = 0,
        val pageCount: Int = 1,
        val encrypted: Boolean = false,
        val askPassphrase: Boolean = false,
    ) : QrExportState

    data class Failed(val error: UiError) : QrExportState
}
