// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

sealed interface FwknopTagContent {
    data object Foreign : FwknopTagContent
    data object Legacy : FwknopTagContent
    data object Unsupported : FwknopTagContent
    data class Found(val digests: List<NfcTagDigest>) : FwknopTagContent
}

object FwknopNdef {
    const val MIME_TYPE = "application/nfckey"
    private const val RECORD_ID = "fwknop/h1"
    private const val LEGACY_RECORD_ID = "fwknop"
    fun record(digest: NfcTagDigest): NdefRecord = NdefRecord(
        NdefRecord.TNF_MIME_MEDIA,
        MIME_TYPE.toByteArray(),
        RECORD_ID.toByteArray(),
        digest.bytes,
    )

    fun isFwknopRecord(record: NdefRecord): Boolean =
        record.id.contentEquals(RECORD_ID.toByteArray()) || isLegacyRecord(record)

    private fun isLegacyRecord(record: NdefRecord): Boolean =
        record.id.contentEquals(LEGACY_RECORD_ID.toByteArray())

    fun digest(record: NdefRecord): NfcTagDigest? =
        record.takeIf { it.id.contentEquals(RECORD_ID.toByteArray()) && it.payload.size == NfcTagDigest.SIZE }
            ?.let { NfcTagDigest(it.payload) }

    fun read(tag: Tag): FwknopTagContent {
        val ndef = Ndef.get(tag)
        val records = ndef?.cachedNdefMessage?.records.orEmpty().filter(::isFwknopRecord)
        val digests = records.mapNotNull(::digest)
        val rewritable = ndef?.isWritable ?: (NdefFormatable.get(tag) != null)
        return when {
            digests.isNotEmpty() -> FwknopTagContent.Found(digests)
            !rewritable -> FwknopTagContent.Unsupported
            records.isNotEmpty() && records.all(::isLegacyRecord) -> FwknopTagContent.Legacy
            else -> FwknopTagContent.Foreign
        }
    }
}

enum class NfcWriteResult { WRITTEN, UNSUPPORTED, READ_ONLY, TOO_SMALL, LOST, FAILED }
class NfcTagWriter @Inject constructor() {
    suspend fun write(
        tag: Tag,
        compose: suspend (existing: List<NfcTagDigest>) -> List<NfcTagDigest>,
    ): NfcWriteResult = try {
        Ndef.get(tag)?.let { writeNdef(it, compose) } ?: formatBlank(tag, compose(emptyList()))
    } catch (e: CancellationException) {
        throw e
    } catch (_: TagLostException) {
        NfcWriteResult.LOST
    } catch (_: IOException) {
        NfcWriteResult.LOST
    } catch (_: FormatException) {
        NfcWriteResult.FAILED
    } catch (_: RuntimeException) {
        NfcWriteResult.FAILED
    }

    suspend fun erase(tag: Tag): NfcWriteResult = write(tag) { emptyList() }
    fun wipe(tag: Tag): NfcWriteResult = try {
        Ndef.get(tag)?.use {
            it.connect()
            if (!it.isWritable) return NfcWriteResult.READ_ONLY
            it.writeNdefMessage(message(emptyList()))
            NfcWriteResult.WRITTEN
        } ?: formatBlank(tag, emptyList())
    } catch (_: TagLostException) {
        NfcWriteResult.LOST
    } catch (_: IOException) {
        NfcWriteResult.LOST
    } catch (_: FormatException) {
        NfcWriteResult.FAILED
    } catch (_: RuntimeException) {
        NfcWriteResult.FAILED
    }

    private suspend fun writeNdef(
        ndef: Ndef,
        compose: suspend (List<NfcTagDigest>) -> List<NfcTagDigest>,
    ): NfcWriteResult = ndef.use {
        it.connect()
        if (!it.isWritable) return NfcWriteResult.READ_ONLY
        val (fwknop, others) = it.ndefMessage?.records.orEmpty()
            .filterNot { record -> record.tnf == NdefRecord.TNF_EMPTY }
            .partition(FwknopNdef::isFwknopRecord)
        val digests = compose(fwknop.mapNotNull(FwknopNdef::digest))
        val message = message(digests.map(FwknopNdef::record) + others)
        if (message.byteArrayLength > it.maxSize) return NfcWriteResult.TOO_SMALL
        it.writeNdefMessage(message)
        NfcWriteResult.WRITTEN
    }

    private fun formatBlank(tag: Tag, digests: List<NfcTagDigest>): NfcWriteResult {
        val formatable = NdefFormatable.get(tag) ?: return NfcWriteResult.UNSUPPORTED
        formatable.use {
            it.connect()
            it.format(message(digests.map(FwknopNdef::record)))
        }
        return NfcWriteResult.WRITTEN
    }

    private fun message(records: List<NdefRecord>): NdefMessage = NdefMessage(
        records.ifEmpty { listOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)) }
            .toTypedArray()
    )
}
