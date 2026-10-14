// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRegistry @Inject constructor() {
    private val counter = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, PendingIntent>()
    private val results = ConcurrentHashMap<Int, Intent>()
    fun register(pendingIntent: PendingIntent): InteractionId {
        val id = counter.incrementAndGet()
        pending[id] = pendingIntent
        return InteractionId(id)
    }

    fun intentSender(id: InteractionId): IntentSender? = pending[id.value]?.intentSender
    fun complete(id: InteractionId, result: Intent?) {
        pending.remove(id.value)
        if (result != null) results[id.value] = result
    }

    fun consumeResult(id: InteractionId): Intent? = results.remove(id.value)
}
