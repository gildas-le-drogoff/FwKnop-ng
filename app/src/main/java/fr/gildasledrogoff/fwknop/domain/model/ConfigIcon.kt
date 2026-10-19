// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

enum class ConfigIcon {
    APP,

    HOME, COTTAGE, BUILDING, GARAGE, OFFICE, STORE, FACTORY, SCHOOL, BOAT, CAMPING, VACATION, WORKSHOP,

    SERVER, DATABASE, STORAGE, CLOUD, WEB, ROUTER, NETWORK, COMPUTER, LAPTOP, TERMINAL, VPN, MAIL, BACKUP, BOARD,

    CAMERA, SMART_HOME, LIGHTING, MEDIA, GAMES, PRINTER, TV, SPEAKER, HEATING, DOORBELL;

    companion object {
        fun parse(name: String?): ConfigIcon = entries.firstOrNull { it.name == name } ?: APP
    }
}
