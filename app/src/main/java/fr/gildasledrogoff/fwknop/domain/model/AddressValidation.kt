package fr.gildasledrogoff.fwknop.domain.model

import org.apache.commons.validator.routines.DomainValidator
import org.apache.commons.validator.routines.InetAddressValidator

private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")
fun String.isIpAddress(): Boolean = InetAddressValidator.getInstance().isValid(this)
fun String.isDomainName(): Boolean = DomainValidator.getInstance().isValid(this)
fun String.isPort(): Boolean = toIntOrNull()?.let { it in 1..ServerPort.MAX } ?: false
fun String.hasBase64Length(): Boolean = length % 4 == 0
fun String.hasBase64Chars(): Boolean = BASE64_PATTERN.matches(this)
