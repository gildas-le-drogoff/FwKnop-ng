// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.fakes
import fr.gildasledrogoff.fwknop.domain.repository.PortProbeRepository

class FakePortProbeRepository(
    private val reachable: (port: Int, attempt: Int) -> Boolean = { _, _ -> false },
) : PortProbeRepository {
    val probes = mutableListOf<Pair<String, Int>>()
    override suspend fun isTcpReachable(address: String, port: Int): Boolean {
        probes += address to port
        return reachable(port, probes.count { it.second == port })
    }
}
