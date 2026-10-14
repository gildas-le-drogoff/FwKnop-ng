// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

import org.apache.commons.validator.routines.DomainValidator
import org.apache.commons.validator.routines.InetAddressValidator

private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")
fun String.isIpAddress(): Boolean = InetAddressValidator.getInstance().isValid(this)


fun String.isIpv4Address(): Boolean = InetAddressValidator.getInstance().isValidInet4Address(this)


private val INTERNAL_TLDS = arrayOf("internal", "lan", "home", "corp", "intranet", "private", "local")
private val DOMAIN_VALIDATOR = DomainValidator.getInstance(
    true,
    listOf(DomainValidator.Item(DomainValidator.ArrayType.LOCAL_PLUS, *INTERNAL_TLDS)),
)
fun String.isDomainName(): Boolean = DOMAIN_VALIDATOR.isValid(this)
fun String.isPort(): Boolean = toIntOrNull()?.let { it in 1..ServerPort.MAX } ?: false
fun String.hasBase64Length(): Boolean = length % 4 == 0
fun String.hasBase64Chars(): Boolean = BASE64_PATTERN.matches(this)
