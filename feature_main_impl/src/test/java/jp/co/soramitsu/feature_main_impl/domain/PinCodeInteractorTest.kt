/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PinCodeInteractorTest {

    @Rule
    @JvmField
    var schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var userRepository: UserRepository
    @Mock
    private lateinit var credentialsRepository: CredentialsRepository
    @Mock
    private lateinit var ethereumRepository: EthereumRepository
    @Mock
    private lateinit var walletRepository: WalletRepository

    private lateinit var interactor: PinCodeInteractor
    private val pin = "1234"

    @Before
    fun setUp() {
        given(userRepository.retrievePin()).willReturn(pin)
        interactor = PinCodeInteractor(userRepository, credentialsRepository, walletRepository)
    }

    @Test
    fun `save pin called`() {
        interactor.savePin(pin)
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(userRepository).savePin(pin)
    }

    @Test
    fun `check pin called`() {
        interactor.checkPin(pin)
            .test()
            .assertComplete()
            .assertNoErrors()
    }

    @Test
    fun `check pin called with wrong pin`() {
        interactor.checkPin("1111")
            .test()
            .assertError(SoraException::class.java)
            .assertErrorMessage(ResponseCode.WRONG_PIN_CODE.toString())
    }

    @Test
    fun `is pin set called`() {
        interactor.isCodeSet()
            .test()
            .assertResult(true)
    }

    @Test
    fun `is pin set called without setting`() {
        given(userRepository.retrievePin()).willReturn("")

        interactor.isCodeSet()
            .test()
            .assertResult(false)
    }

    @Test
    fun `reset user called`() {
        interactor.resetUser()

        verify(userRepository).clearUserData()
    }
}
