package fr.gildasledrogoff.fwknop.data.datasource

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.utils.helpers.InteractionRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OpenPgpDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: InteractionRegistry,
) {
    private val bindLock = Mutex()
    @Volatile
    private var connection: OpenPgpServiceConnection? = null

    sealed interface Result {
        data class Success(val data: Intent, val output: ByteArray) : Result
        data class InteractionRequired(val id: InteractionId) : Result
        data class Error(val message: String?) : Result
    }

    fun isInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(PROVIDER, 0) }.isSuccess

    suspend fun execute(request: Intent, input: ByteArray?): Result {
        val output = ByteArrayOutputStream()
        val stream: InputStream? = input?.let(::ByteArrayInputStream)
        val result = OpenPgpApi(context, service()).executeApi(request, stream, output)
        return result.toResult(output.toByteArray())
    }

    fun interactionResult(id: InteractionId?): Intent? = id?.let(registry::consumeResult)
    private fun Intent.toResult(output: ByteArray): Result =
        when (getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> Result.Success(this, output)
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> pendingIntent()
                ?.let { Result.InteractionRequired(registry.register(it)) }
                ?: Result.Error(null)

            else -> Result.Error(errorMessage())
        }

    private fun Intent.pendingIntent(): PendingIntent? =
        IntentCompat.getParcelableExtra(this, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java)

    private fun Intent.errorMessage(): String? =
        IntentCompat.getParcelableExtra(
            this,
            OpenPgpApi.RESULT_ERROR,
            OpenPgpError::class.java
        )?.message

    private suspend fun service(): IOpenPgpService2 = bindLock.withLock {
        connection?.takeIf { it.isBound }?.service
            ?: withTimeoutOrNull(BIND_TIMEOUT_MS) { bind() }
            ?: error("OpenKeychain service did not respond")
    }

    private suspend fun bind(): IOpenPgpService2 = suspendCancellableCoroutine { continuation ->
        val listener = object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) {
                if (continuation.isActive) continuation.resume(service)
            }

            override fun onError(e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        val pending = OpenPgpServiceConnection(context, PROVIDER, listener)
        connection = pending
        
        continuation.invokeOnCancellation {
            runCatching { pending.unbindFromService() }
            if (connection === pending) connection = null
        }
        pending.bindToService()
    }

    companion object {
        const val PROVIDER = "org.sufficientlysecure.keychain"
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}
