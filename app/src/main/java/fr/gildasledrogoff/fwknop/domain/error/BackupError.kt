package fr.gildasledrogoff.fwknop.domain.error

sealed interface BackupError {
    data object PassphraseTooShort : BackupError
    data object NotABackup : BackupError
    data object WrongPassphrase : BackupError

    
    data object PartialBackup : BackupError

    
    data object UnreadableQr : BackupError

    data class Config(val error: ConfigError) : BackupError
}
