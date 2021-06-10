/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
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
class MainInteractorTest {

    @Rule
    @JvmField
    var schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    private lateinit var interactor: MainInteractor

    @Before
    fun setUp() {
        interactor = MainInteractor(
            userRepository,
            credentialsRepository,
        )
    }

    @Test
    fun `getMnemonic() function returns not empty mnemonic`() {
        val mnemonic = "test mnemonic"
        given(credentialsRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(mnemonic)

        verify(credentialsRepository).retrieveMnemonic()
    }

    @Test
    fun `getMnemonic() function returns empty mnemonic`() {
        val mnemonic = ""
        given(credentialsRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertErrorMessage(ResponseCode.GENERAL_ERROR.toString())

        verify(credentialsRepository).retrieveMnemonic()
    }

    @Test
    fun `getInviteCode() calls userRepository getParentInviteCode()`() {
        val expectedResult = "parentInviteCode"
        given(userRepository.getParentInviteCode()).willReturn(Single.just(expectedResult))

        interactor.getInviteCode()
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getParentInviteCode()
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() {
        val expectedResult = "version"
        given(userRepository.getAppVersion()).willReturn(Single.just(expectedResult))

        interactor.getAppVersion()
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getAppVersion()
    }
}
