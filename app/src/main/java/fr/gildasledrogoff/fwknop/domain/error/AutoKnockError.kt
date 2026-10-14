package fr.gildasledrogoff.fwknop.domain.error

sealed interface AutoKnockError {
    /** The app lock is on and the vault was locked: the key cannot be read without the user. */
    data object VaultLocked : AutoKnockError
    data object ConfigMissing : AutoKnockError

    /** The config asks for a key or source IP, or uses GPG: it cannot knock unattended. */
    data object NeedsUser : AutoKnockError
    data class Knock(val error: KnockError) : AutoKnockError
}
