package fr.gildasledrogoff.fwknop.data.repository

import android.content.Intent
import android.util.Base64
import fr.gildasledrogoff.fwknop.data.datasource.OpenPgpDataSource
import fr.gildasledrogoff.fwknop.data.datasource.OpenPgpDataSource.Result
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyRole
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.repository.GpgRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import org.openintents.openpgp.util.OpenPgpApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpgRepositoryImpl @Inject constructor(
    private val openPgp: OpenPgpDataSource,
    private val dispatchers: DispatcherProvider,
) : GpgRepository {
    override suspend fun encrypt(
        plaintext: String,
        keys: Encryption.Gpg,
        resume: InteractionId?,
    ): DomainResult<String, GpgError> {
        val request = openPgp.interactionResult(resume) ?: encryptRequest(keys)
        val input = plaintext.toByteArray(Charsets.US_ASCII)
        return run(request, input, GpgError.Failed(null)) { result ->
            Base64.encodeToString(result.output, Base64.NO_WRAP).takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun selectKey(
        role: GpgKeyRole,
        resume: InteractionId?
    ): DomainResult<GpgKeyId, GpgError> {
        openPgp.interactionResult(resume)?.selectedKey(role)
            ?.let { return DomainResult.Success(it) }
        return run(selectRequest(role), input = null, GpgError.NoKeySelected) {
            it.data.resultKey(role)
        }
    }

    private suspend fun <T : Any> run(
        request: Intent,
        input: ByteArray?,
        emptyResult: GpgError,
        extract: (Result.Success) -> T?,
    ): DomainResult<T, GpgError> {
        if (!openPgp.isInstalled()) return DomainResult.Failure(GpgError.NotInstalled)
        val result = withContext(dispatchers.io) {
            runCatching { openPgp.execute(request, input) }.getOrElse { Result.Error(it.message) }
        }
        return when (result) {
            is Result.Success -> extract(result)?.let { DomainResult.Success(it) }
                ?: DomainResult.Failure(emptyResult)

            is Result.InteractionRequired -> DomainResult.Failure(
                GpgError.InteractionRequired(
                    result.id
                )
            )

            is Result.Error -> DomainResult.Failure(GpgError.Failed(result.message))
        }
    }

    private fun encryptRequest(keys: Encryption.Gpg) = Intent(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT)
        .putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, keys.signer.value)
        .putExtra(OpenPgpApi.EXTRA_KEY_IDS, longArrayOf(keys.recipient.value))
        .putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, false)

    private fun selectRequest(role: GpgKeyRole) = when (role) {
        GpgKeyRole.RECIPIENT -> Intent(OpenPgpApi.ACTION_GET_KEY_IDS)
            .putExtra(OpenPgpApi.EXTRA_USER_IDS, arrayOf(""))

        GpgKeyRole.SIGNER -> Intent(OpenPgpApi.ACTION_GET_SIGN_KEY_ID)
    }

    private fun Intent.selectedKey(role: GpgKeyRole): GpgKeyId? = when (role) {
        GpgKeyRole.RECIPIENT -> getLongArrayExtra(EXTRA_KEY_IDS_SELECTED)?.firstOrNull()
        GpgKeyRole.SIGNER -> getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0L).takeIf { it != 0L }
    }?.let(::GpgKeyId)

    private fun Intent.resultKey(role: GpgKeyRole): GpgKeyId? = when (role) {
        GpgKeyRole.RECIPIENT -> getLongArrayExtra(RESULT_KEY_IDS)?.firstOrNull()
        GpgKeyRole.SIGNER -> getLongExtra(RESULT_SIGN_KEY_ID, 0L).takeIf { it != 0L }
    }?.let(::GpgKeyId)

    private companion object {
        const val EXTRA_KEY_IDS_SELECTED = "key_ids_selected"
        const val RESULT_KEY_IDS = "key_ids"
        const val RESULT_SIGN_KEY_ID = "sign_key_id"
    }
}
