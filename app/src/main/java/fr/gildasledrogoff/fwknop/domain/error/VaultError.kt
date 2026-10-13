package fr.gildasledrogoff.fwknop.domain.error

sealed interface VaultError {
    data object AuthenticationRequired : VaultError
    data object DeviceNotSecure : VaultError
    data object KeyInvalidated : VaultError
    data class Unavailable(val cause: Throwable) : VaultError
}
