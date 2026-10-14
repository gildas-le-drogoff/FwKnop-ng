package fr.gildasledrogoff.fwknop.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.gildasledrogoff.fwknop.data.datasource.spa.AndroidSpaUsername
import fr.gildasledrogoff.fwknop.data.datasource.spa.SecureSpaEntropy
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaEntropy
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaUsernameProvider
import fr.gildasledrogoff.fwknop.data.repository.BackupRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.GpgRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.PortProbeRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.PublicIpRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SettingsRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SpaPacketRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository
import fr.gildasledrogoff.fwknop.domain.repository.GpgRepository
import fr.gildasledrogoff.fwknop.domain.repository.KnockTimerScheduler
import fr.gildasledrogoff.fwknop.domain.repository.PortProbeRepository
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import fr.gildasledrogoff.fwknop.presentation.service.KnockTimerSchedulerImpl
import fr.gildasledrogoff.fwknop.utils.dispatcher.DefaultDispatcherProvider
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.AndroidLocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.AndroidRuntimePermissions
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import fr.gildasledrogoff.fwknop.utils.helpers.RuntimePermissions

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {
    @Binds
    fun bindDispatchers(impl: DefaultDispatcherProvider): DispatcherProvider
    @Binds
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds
    fun bindPublicIpRepository(impl: PublicIpRepositoryImpl): PublicIpRepository
    @Binds
    fun bindSpaPacketRepository(impl: SpaPacketRepositoryImpl): SpaPacketRepository
    @Binds
    fun bindSpaTransportRepository(impl: SpaTransportRepositoryImpl): SpaTransportRepository
    @Binds
    fun bindPortProbeRepository(impl: PortProbeRepositoryImpl): PortProbeRepository
    @Binds
    fun bindGpgRepository(impl: GpgRepositoryImpl): GpgRepository
    @Binds
    fun bindKnockTimerScheduler(impl: KnockTimerSchedulerImpl): KnockTimerScheduler
    @Binds
    fun bindSpaEntropy(impl: SecureSpaEntropy): SpaEntropy
    @Binds
    fun bindSpaUsername(impl: AndroidSpaUsername): SpaUsernameProvider
    @Binds
    fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
    @Binds
    fun bindLocalNetworkAccess(impl: AndroidLocalNetworkAccess): LocalNetworkAccess
    @Binds
    fun bindRuntimePermissions(impl: AndroidRuntimePermissions): RuntimePermissions
}
