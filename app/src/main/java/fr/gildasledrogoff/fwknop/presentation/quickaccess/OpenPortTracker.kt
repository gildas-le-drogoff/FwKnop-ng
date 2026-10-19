// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.quickaccess

import fr.gildasledrogoff.fwknop.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class OpenPort(val untilMillis: Long, val keptOpen: Boolean)

@Singleton
class OpenPortTracker @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val _open = MutableStateFlow<Map<String, OpenPort>>(emptyMap())
    val open: StateFlow<Map<String, OpenPort>> = _open.asStateFlow()
    private val expiries = ConcurrentHashMap<String, Job>()

    fun opened(nickname: String, seconds: Int, keptOpen: Boolean) {
        val port = OpenPort(clock.millis() + seconds * MILLIS, keptOpen)
        _open.update { it + (nickname to port) }
        val expiry = scope.launch {
            delay(seconds * MILLIS)
            _open.update { if (it[nickname] == port) it - nickname else it }
        }
        expiries.put(nickname, expiry)?.cancel()
    }

    fun closed(nickname: String) {
        expiries.remove(nickname)?.cancel()
        _open.update { it - nickname }
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}
