package fr.gildasledrogoff.fwknop.data.local.security

import java.util.UUID
import javax.inject.Inject

fun interface KeyAliasFactory {
    fun next(): String
}

class RandomKeyAliasFactory @Inject constructor() : KeyAliasFactory {
    override fun next(): String = KeyAliases.PREFIX + UUID.randomUUID()
}

object KeyAliases {
    const val PREFIX = "fwknop_master_"
    fun isOurs(alias: String): Boolean = alias.startsWith(PREFIX)
}
