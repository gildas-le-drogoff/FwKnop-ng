package fr.gildasledrogoff.fwknop.domain.model

enum class AutoLockDelay(val seconds: Long?) {
    NEVER(null),
    SECONDS_30(30),
    MINUTE_1(60),
    MINUTES_5(300),
    MINUTES_15(900),
}

enum class VaultState {
    LOCKED,
    UNLOCKED,
}

data class VaultStatus(val state: VaultState, val lockEnabled: Boolean)
