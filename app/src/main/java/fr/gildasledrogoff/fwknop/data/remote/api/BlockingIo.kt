// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean


internal suspend fun <T> abortable(abort: () -> Unit, block: () -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) abort()
        }
    }
    try {
        runInterruptible { block() }
    } catch (e: Exception) {

        ensureActive()
        throw e
    } finally {
        finished.set(true)
        watchdog.cancel()
    }
}


internal class Retransmissions(val sendAtMs: List<Long>, val giveUpMs: Long)


internal fun <T : Any> DatagramSocket.exchange(
    request: ByteArray,
    schedule: Retransmissions,
    bufferSize: Int,
    parse: (datagram: ByteArray, length: Int) -> T?,
): T {
    val buffer = ByteArray(bufferSize)
    val start = System.nanoTime()
    var sent = 0
    while (true) {
        val elapsed = (System.nanoTime() - start) / NANOS_PER_MS
        if (sent < schedule.sendAtMs.size && elapsed >= schedule.sendAtMs[sent]) {
            send(DatagramPacket(request, request.size))
            sent++
            continue
        }
        val wakeAt = schedule.sendAtMs.getOrNull(sent) ?: schedule.giveUpMs
        if (elapsed >= wakeAt) throw SocketTimeoutException("no answer to $sent requests in ${schedule.giveUpMs} ms")
        soTimeout = (wakeAt - elapsed).toInt().coerceAtLeast(1)
        val packet = DatagramPacket(buffer, buffer.size)
        try {
            receive(packet)
        } catch (_: SocketTimeoutException) {
            continue
        }
        parse(buffer, packet.length)?.let { return it }
    }
}

private const val NANOS_PER_MS = 1_000_000L
