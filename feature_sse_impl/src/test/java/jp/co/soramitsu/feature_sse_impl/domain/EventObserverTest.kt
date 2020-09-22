/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.domain

import io.reactivex.Observable
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_sse_api.interfaces.EventRepository
import jp.co.soramitsu.feature_sse_api.model.EthRegistrationStartedEvent
import jp.co.soramitsu.feature_sse_api.model.Event
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class EventObserverTest {

    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock lateinit var eventRepository: EventRepository
    @Mock lateinit var ethereumRepository: EthereumRepository
    @Mock lateinit var walletRepository: WalletRepository
    @Mock lateinit var didRepository: DidRepository

    private lateinit var eventObserver: EventObserver

    @Before fun setUp() {
        eventObserver = EventObserver(eventRepository, ethereumRepository, walletRepository, didRepository)
    }

    @Test fun `observing new events flow`() {
        val operationId = "test operation id"
        val testEvent = mock(EthRegistrationStartedEvent::class.java)
        given(eventRepository.observeEvents()).willReturn(Observable.just(testEvent))
        given(testEvent.getEventType()).willReturn(Event.Type.ETH_REGISTRATION_STARTED)
        given(testEvent.operationId).willReturn(operationId)

        eventObserver.observeNewEvents()

        verify(eventRepository).observeEvents()
        verify(ethereumRepository).registrationStarted(operationId)
    }
}