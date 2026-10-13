package fr.gildasledrogoff.fwknop.presentation.state

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

@Immutable
sealed interface UiError {
    data class Message(@param:StringRes val resId: Int) : UiError
    data class Detailed(@param:StringRes val resId: Int, val detail: String) : UiError
}

@Composable
fun UiError.text(): String = when (this) {
    is UiError.Message -> stringResource(resId)
    is UiError.Detailed -> stringResource(resId, detail)
}
