package fr.gildasledrogoff.fwknop.data.local.database

object SqlCipher {
    private val loaded: Unit by lazy { System.loadLibrary("sqlcipher") }
    fun ensureLoaded() = loaded
}
