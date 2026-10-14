package fr.gildasledrogoff.fwknop.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.gildasledrogoff.fwknop.data.repository.AutoKnockRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.WifiNetworkRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockMonitor
import fr.gildasledrogoff.fwknop.domain.repository.AutoKnockRepository
import fr.gildasledrogoff.fwknop.domain.repository.WifiNetworkRepository
import fr.gildasledrogoff.fwknop.presentation.service.AutoKnockController
import fr.gildasledrogoff.fwknop.utils.helpers.AndroidLocationAccess
import fr.gildasledrogoff.fwknop.utils.helpers.LocationAccess

@Module
@InstallIn(SingletonComponent::class)
interface AutoKnockModule {
    @Binds
    fun bindAutoKnockRepository(impl: AutoKnockRepositoryImpl): AutoKnockRepository
    @Binds
    fun bindWifiNetworkRepository(impl: WifiNetworkRepositoryImpl): WifiNetworkRepository
    @Binds
    fun bindAutoKnockMonitor(impl: AutoKnockController): AutoKnockMonitor
    @Binds
    fun bindLocationAccess(impl: AndroidLocationAccess): LocationAccess
}
