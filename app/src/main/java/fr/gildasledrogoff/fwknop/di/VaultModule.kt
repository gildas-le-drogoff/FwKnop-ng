// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFileStore
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFiles
import fr.gildasledrogoff.fwknop.data.local.security.*
import fr.gildasledrogoff.fwknop.data.repository.*
import fr.gildasledrogoff.fwknop.domain.repository.*

@Module
@InstallIn(SingletonComponent::class)
interface VaultModule {
    @Binds
    fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    fun bindVaultMetadataStore(impl: PrefsVaultMetadataStore): VaultMetadataStore

    @Binds
    fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository

    @Binds
    fun bindMasterKeyStore(impl: AndroidMasterKeyStore): MasterKeyStore

    @Binds
    fun bindKeysetStore(impl: AndroidKeysetStore): KeysetStore

    @Binds
    fun bindKeyAliasFactory(impl: RandomKeyAliasFactory): KeyAliasFactory

    @Binds
    fun bindDatabaseFileStore(impl: DatabaseFiles): DatabaseFileStore

    @Binds
    fun bindSealedPassphraseStore(impl: PrefsSealedPassphraseStore): SealedPassphraseStore

    @Binds
    fun bindSealedExportPassphraseStore(impl: PrefsSealedExportPassphraseStore): SealedExportPassphraseStore

    @Binds
    fun bindSealedQrRepository(impl: SealedQrRepositoryImpl): SealedQrRepository

    @Binds
    fun bindSealedNfcTagKeyStore(impl: PrefsSealedNfcTagKeyStore): SealedNfcTagKeyStore

    @Binds
    fun bindNfcTagRepository(impl: NfcTagRepositoryImpl): NfcTagRepository

    @Binds
    fun bindKnockHistoryRepository(impl: KnockHistoryRepositoryImpl): KnockHistoryRepository
}
