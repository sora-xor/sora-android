/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MainInteractorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var selectNodeRepository: SelectNodeRepository

    private lateinit var interactor: MainInteractor

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = MainInteractor(
            userRepository,
            selectNodeRepository
        )
    }

    @Test
    fun `getCurUserAddress is called`() = runTest {
        val actualUser = SoraAccount("address", "user")
        given(userRepository.getCurSoraAccount()).willReturn(actualUser)


        assertEquals(actualUser.substrateAddress, interactor.getCurUserAddress())
    }

    @Test
    fun `getAvailableLanguagesWithSelected is called`() = runTest {
        val actual = Pair(listOf(Language("ru_Ru", 0, 0)), 0)
        given(userRepository.getAvailableLanguages()).willReturn(actual)


        assertEquals(actual, interactor.getAvailableLanguagesWithSelected())
    }

    @Test
    fun `changeLanguage is called`() = runTest {
        val language = "ru_Ru"

        interactor.changeLanguage(language)

        verify(userRepository).changeLanguage(language)
    }

    @Test
    fun `set biometry enabled is called`() = runTest {
        val biometetryEnabled = false

        interactor.setBiometryEnabled(biometetryEnabled)

        verify(userRepository).setBiometryEnabled(biometetryEnabled)
    }

    @Test
    fun `is biometry enabled is called`() = runTest {
        val biometetryEnabled = false

        given(userRepository.isBiometryEnabled()).willReturn(biometetryEnabled)

        assertEquals(biometetryEnabled, interactor.isBiometryEnabled())
    }

    @Test
    fun `is biometry available is called`() = runTest {
        val biometetryAvailable = false

        given(userRepository.isBiometryAvailable()).willReturn(biometetryAvailable)

        assertEquals(biometetryAvailable, interactor.isBiometryAvailable())
    }

    @Test
    fun `save account name is called`() = runTest {
        val accountname = "accountName"
        val curUser = SoraAccount("address", "user")
        given(userRepository.getCurSoraAccount()).willReturn(curUser)

        interactor.saveAccountName(accountname)

        verify(userRepository).updateAccountName(curUser, accountname)
    }

    @Test
    fun `get account name is called`() = runTest {
        val curUser = SoraAccount("address", "user")

        given(userRepository.getCurSoraAccount()).willReturn(curUser)

        assertEquals(curUser.accountName, interactor.getAccountName())
    }

    @Test
    fun `get sora account list is called`() = runTest {
        val accounts = listOf(SoraAccount("address", "user"), SoraAccount("address2", "user2"))

        given(userRepository.soraAccountsList()).willReturn(accounts)

        assertEquals(accounts, interactor.getSoraAccountsList())
    }

    @Test
    fun `get sora accounts count is called`() = runTest {
        val count = 3

        given(userRepository.getSoraAccountsCount()).willReturn(count)

        assertEquals(count, interactor.getSoraAccountsCount())
    }

    @Test
    fun `flow selected sora account is called`() = runTest {
        val curUser = SoraAccount("address", "user")

        given(userRepository.flowCurSoraAccount()).willReturn(flow { emit(curUser) })

        assertEquals(curUser, interactor.flowCurSoraAccount().first())
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() = runTest {
        val expectedResult = "version"
        mockkObject(OptionsProvider)
        every { OptionsProvider.CURRENT_VERSION_NAME } returns expectedResult
        assertEquals(expectedResult, interactor.getAppVersion())
    }

    @Test
    fun `set cur sora account is called`() = runTest {
        val accountAddress = SoraAccount("acc", "nam")

        interactor.setCurSoraAccount(accountAddress)

        verify(userRepository).setCurSoraAccount(accountAddress)
    }

    @Test
    fun `flowSelectedNode() calls selectedNodeRepository getSelectedNode()`() {
        interactor.flowSelectedNode()

        verify(selectNodeRepository).getSelectedNode()
    }
}
