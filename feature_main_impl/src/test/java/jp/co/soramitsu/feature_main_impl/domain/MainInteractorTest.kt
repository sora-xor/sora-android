/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.mockk.every
import io.mockk.mockkObject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.resourses.Language
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
    private lateinit var selectNodeRepository: SelectNodeRepository

    private lateinit var interactor: MainInteractor

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() {
        mockkObject(OptionsProvider)
        every { OptionsProvider.CURRENT_VERSION_NAME } returns "version"
    }

    private suspend fun setUpModel() {
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = MainInteractor(
            userRepository,
            selectNodeRepository
        )
    }

    @Test
    fun `getCurUserAddress is called`() = runTest {
        setUpModel()
        val actualUser = SoraAccount("address", "user")
        given(userRepository.getCurSoraAccount()).willReturn(actualUser)

        assertEquals(actualUser.substrateAddress, interactor.getCurUserAddress())
    }

    @Test
    fun `getAvailableLanguagesWithSelected is called`() = runTest {
        setUpModel()
        val actual = Pair(listOf(Language("ru_Ru", 0, 0)), 0)
        given(userRepository.getAvailableLanguages()).willReturn(actual)

        assertEquals(actual, interactor.getAvailableLanguagesWithSelected())
    }

    @Test
    fun `changeLanguage is called`() = runTest {
        setUpModel()
        val language = "ru_Ru"

        interactor.changeLanguage(language)

        verify(userRepository).changeLanguage(language)
    }

    @Test
    fun `set biometry enabled is called`() = runTest {
        setUpModel()
        val biometetryEnabled = false

        interactor.setBiometryEnabled(biometetryEnabled)

        verify(userRepository).setBiometryEnabled(biometetryEnabled)
    }

    @Test
    fun `is biometry enabled is called`() = runTest {
        setUpModel()
        val biometetryEnabled = false

        given(userRepository.isBiometryEnabled()).willReturn(biometetryEnabled)

        assertEquals(biometetryEnabled, interactor.isBiometryEnabled())
    }

    @Test
    fun `is biometry available is called`() = runTest {
        setUpModel()
        val biometetryAvailable = false

        given(userRepository.isBiometryAvailable()).willReturn(biometetryAvailable)

        assertEquals(biometetryAvailable, interactor.isBiometryAvailable())
    }

    @Test
    fun `save account name is called`() = runTest {
        setUpModel()
        val accountname = "accountName"
        val curUser = SoraAccount("address", "user")
        given(userRepository.getCurSoraAccount()).willReturn(curUser)

        interactor.saveAccountName(accountname)

        verify(userRepository).updateAccountName(curUser, accountname)
    }

    @Test
    fun `get account name is called`() = runTest {
        setUpModel()
        val curUser = SoraAccount("address", "user")

        given(userRepository.getCurSoraAccount()).willReturn(curUser)

        assertEquals(curUser.accountName, interactor.getAccountName())
    }

    @Test
    fun `get sora account list is called`() = runTest {
        setUpModel()
        val accounts = listOf(SoraAccount("address", "user"), SoraAccount("address2", "user2"))

        given(userRepository.soraAccountsList()).willReturn(accounts)

        assertEquals(accounts, interactor.getSoraAccountsList())
    }

    @Test
    fun `get sora accounts count is called`() = runTest {
        setUpModel()
        val count = 3

        given(userRepository.getSoraAccountsCount()).willReturn(count)

        assertEquals(count, interactor.getSoraAccountsCount())
    }

    @Test
    fun `flow selected sora account is called`() = runTest {
        setUpModel()
        val curUser = SoraAccount("address", "user")

        given(userRepository.flowCurSoraAccount()).willReturn(flow { emit(curUser) })

        assertEquals(curUser, interactor.flowCurSoraAccount().first())
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() = runTest {
        setUpModel()
        assertEquals("version", interactor.getAppVersion())
    }

    @Test
    fun `set cur sora account is called`() = runTest {
        setUpModel()
        val accountAddress = SoraAccount("acc", "nam")

        interactor.setCurSoraAccount(accountAddress)

        verify(userRepository).setCurSoraAccount(accountAddress)
    }

    @Test
    fun `flowSelectedNode() calls selectedNodeRepository getSelectedNode()`() = runTest {
        setUpModel()
        interactor.flowSelectedNode()

        verify(selectNodeRepository).getSelectedNode()
    }
}
