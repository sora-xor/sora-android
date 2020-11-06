/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_sse_api.EventsObservingStarter
import jp.co.soramitsu.feature_sse_api.interfaces.EventDatasource
import jp.co.soramitsu.feature_sse_api.interfaces.EventRepository
import jp.co.soramitsu.feature_sse_impl.EventsObservingStarterImpl
import jp.co.soramitsu.feature_sse_impl.data.mappers.DepositOperationCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegFailedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.EthRegStartedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationCompletedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationFailedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.mappers.OperationStartedEventMapper
import jp.co.soramitsu.feature_sse_impl.data.repository.EventRepositoryImpl
import jp.co.soramitsu.feature_sse_impl.data.repository.datasource.PrefsEventDatasource
import jp.co.soramitsu.feature_sse_impl.domain.EventObserver
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import javax.inject.Singleton

@Module
class EventModule {

    @Singleton
    @Provides
    fun provideEventRepository(eventRepository: EventRepositoryImpl): EventRepository = eventRepository

    @Singleton
    @Provides
    fun provideEventObserver(
        eventRepository: EventRepository,
        ethereumRepository: EthereumRepository,
        walletRepository: WalletRepository,
        didRepository: DidRepository
    ): EventObserver {
        return EventObserver(eventRepository, ethereumRepository, walletRepository, didRepository)
    }

    @Provides
    @Singleton
    fun provideEventsObservingStarter(contextManager: ContextManager): EventsObservingStarter = EventsObservingStarterImpl(contextManager)

    @Provides
    @Singleton
    fun provideEthRegCompletedEventMapper(): EthRegCompletedEventMapper {
        return EthRegCompletedEventMapper()
    }

    @Provides
    @Singleton
    fun provideEthRegFailedEventMapper(): EthRegFailedEventMapper {
        return EthRegFailedEventMapper()
    }

    @Provides
    @Singleton
    fun provideEthRegistrationEventMapper(): EthRegStartedEventMapper {
        return EthRegStartedEventMapper()
    }

    @Provides
    @Singleton
    fun provideOperationStartedEventMapper(): OperationStartedEventMapper {
        return OperationStartedEventMapper()
    }

    @Provides
    @Singleton
    fun provideOperationCompletedEventMapper(): OperationCompletedEventMapper {
        return OperationCompletedEventMapper()
    }

    @Provides
    @Singleton
    fun provideDepositOperationCompletedEventMapper(): DepositOperationCompletedEventMapper {
        return DepositOperationCompletedEventMapper()
    }

    @Provides
    @Singleton
    fun provideOperationFailedEventMapper(): OperationFailedEventMapper {
        return OperationFailedEventMapper()
    }

    @Provides
    @Singleton
    fun provideEventDatasource(preferences: Preferences): EventDatasource {
        return PrefsEventDatasource(preferences)
    }
}