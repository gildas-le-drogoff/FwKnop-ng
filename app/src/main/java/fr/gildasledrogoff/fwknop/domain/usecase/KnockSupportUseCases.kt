// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ScheduleKnockTimerUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: KnockTimerScheduler,
) {
    suspend operator fun invoke(outcome: KnockOutcome): KnockTimerOutcome {
        if (!settings.settings.first().notificationsEnabled) return KnockTimerOutcome.NONE
        val plan = planFor(outcome.request) ?: return KnockTimerOutcome.NONE
        return when {
            !scheduler.canNotify() ->
                if (plan is KnockTimerPlan.KeepOpen) KnockTimerOutcome.NOTIFICATIONS_BLOCKED else KnockTimerOutcome.NONE

            scheduler.schedule(plan) -> KnockTimerOutcome.SCHEDULED
            else -> fallBackToCountdown(outcome.request, plan)
        }
    }

    private fun fallBackToCountdown(
        request: KnockRequest,
        plan: KnockTimerPlan
    ): KnockTimerOutcome {
        val keepOpen = plan as? KnockTimerPlan.KeepOpen ?: return KnockTimerOutcome.NONE
        scheduler.schedule(KnockTimerPlan.Countdown(request.config.nickname, keepOpen.seconds))
        return KnockTimerOutcome.KEEP_OPEN_UNAVAILABLE
    }

    private fun planFor(request: KnockRequest): KnockTimerPlan? {
        val access = request.config.access.portAccess ?: return null
        val seconds = access.timeoutSeconds ?: return null

        val unattended =
            request.config.method != KnockMethod.Spa || request.config.crypto.encryption is Encryption.Rijndael
        val canReknock = access.keepOpen && unattended
        return if (canReknock) {
            KnockTimerPlan.KeepOpen(request, seconds)
        } else {
            KnockTimerPlan.Countdown(request.config.nickname, seconds)
        }
    }
}

class NeedsTimerNotificationUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(config: SpaConfig): Boolean =
        settings.settings.first().notificationsEnabled && config.access.portAccess?.timeoutSeconds != null
}

class GetNfcKnockConfigUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val configs: ConfigRepository,
    private val nfcTags: NfcTagRepository,
) {
    suspend operator fun invoke(digests: List<NfcTagDigest>): DomainResult<List<SpaConfig>, KnockError> {
        if (!settings.settings.first().nfcKnockEnabled) return DomainResult.Failure(KnockError.NfcDisabled)
        val all = when (val result = configs.getAll()) {
            is DomainResult.Success -> result.data
            is DomainResult.Failure -> return DomainResult.Failure(KnockError.Config(result.error))
        }
        val matched = nfcTags.match(digests, all.map { it.nickname })
            .filterNotNull()
            .distinct()
            .mapNotNull { nickname -> all.firstOrNull { it.nickname == nickname } }
        return if (matched.isEmpty()) DomainResult.Failure(KnockError.NfcTagUnknown) else DomainResult.Success(
            matched
        )
    }
}

class CreateNfcTagDigestUseCase @Inject constructor(private val nfcTags: NfcTagRepository) {
    suspend operator fun invoke(nickname: Nickname): NfcTagDigest = nfcTags.digest(nickname)
}

class OtherNfcTagDigestsUseCase @Inject constructor(private val nfcTags: NfcTagRepository) {
    suspend operator fun invoke(
        existing: List<NfcTagDigest>,
        nickname: Nickname
    ): List<NfcTagDigest> {
        val matched = nfcTags.match(existing, listOf(nickname))
        return existing.filterIndexed { index, _ -> matched[index] == null }.distinct()
    }
}

class SelectGpgKeyUseCase @Inject constructor(private val gpg: GpgRepository) {
    suspend operator fun invoke(
        role: GpgKeyRole,
        resume: InteractionId? = null
    ): DomainResult<GpgKeyId, GpgError> =
        gpg.selectKey(role, resume)
}
