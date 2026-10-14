// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

sealed interface DomainResult<out T, out E> {
    data class Success<T>(val data: T) : DomainResult<T, Nothing>
    data class Failure<E>(val error: E) : DomainResult<Nothing, E>
}

inline fun <T, E, R> DomainResult<T, E>.map(transform: (T) -> R): DomainResult<R, E> = when (this) {
    is DomainResult.Success -> DomainResult.Success(transform(data))
    is DomainResult.Failure -> this
}

inline fun <T, E, R> DomainResult<T, E>.flatMap(transform: (T) -> DomainResult<R, E>): DomainResult<R, E> =
    when (this) {
        is DomainResult.Success -> transform(data)
        is DomainResult.Failure -> this
    }

inline fun <T, E, F> DomainResult<T, E>.mapError(transform: (E) -> F): DomainResult<T, F> =
    when (this) {
        is DomainResult.Success -> this
        is DomainResult.Failure -> DomainResult.Failure(transform(error))
    }
