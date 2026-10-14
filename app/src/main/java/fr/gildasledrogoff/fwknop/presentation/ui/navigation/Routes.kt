package fr.gildasledrogoff.fwknop.presentation.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ConfigListRoute

@Serializable
data class ConfigEditRoute(val nickname: String? = null) {
    companion object {
        const val ARG_NICKNAME = "nickname"
    }
}

@Serializable
data object SettingsRoute

@Serializable
data object HelpRoute

@Serializable
data object KnockHistoryRoute

@Serializable
data class NfcWriteRoute(val nickname: String) {
    companion object {
        const val ARG_NICKNAME = "nickname"
    }
}


@Serializable
data class QrExportRoute(val nicknames: List<String>)

@Serializable
data class DiagnosticRoute(val nickname: String) {
    companion object {
        const val ARG_NICKNAME = "nickname"
    }
}
