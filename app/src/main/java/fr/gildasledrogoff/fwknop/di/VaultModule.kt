package fr.gildasledrogoff.fwknop.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFileStore
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFiles
import fr.gildasledrogoff.fwknop.data.local.security.AndroidKeysetStore
import fr.gildasledrogoff.fwknop.data.local.security.AndroidMasterKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.KeyAliasFactory
import fr.gildasledrogoff.fwknop.data.local.security.KeysetStore
import fr.gildasledrogoff.fwknop.data.local.security.MasterKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedExportPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedNfcTagKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsSealedPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.PrefsVaultMetadataStore
import fr.gildasledrogoff.fwknop.data.local.security.RandomKeyAliasFactory
import fr.gildasledrogoff.fwknop.data.local.security.SealedExportPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.SealedNfcTagKeyStore
import fr.gildasledrogoff.fwknop.data.local.security.SealedPassphraseStore
import fr.gildasledrogoff.fwknop.data.local.security.VaultMetadataStore
import fr.gildasledrogoff.fwknop.data.repository.ConfigRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.KnockHistoryRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.NfcTagRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SealedQrRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.VaultRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockHistoryRepository
import fr.gildasledrogoff.fwknop.domain.repository.NfcTagRepository
import fr.gildasledrogoff.fwknop.domain.repository.SealedQrRepository
import fr.gildasledrogoff.fwknop.domain.repository.VaultRepository

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
