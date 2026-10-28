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

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import java.math.BigDecimal
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.sora.substrate.models.BlockEntry
import jp.co.soramitsu.sora.substrate.models.BlockResponse
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class WalletInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var walletRepository: WalletRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var builder: TransactionBuilder

    private lateinit var interactor: WalletInteractor

    private val soraAccount = SoraAccount("address", "name")

    private val irohaData: IrohaData =
        IrohaData(address = "abcdef", claimSignature = "qweasdzc", publicKey = "publickey")

    @Before
    fun setUp() = runTest {
        mockkStatic(String::extrinsicHash)
        every { "0x112323345".extrinsicHash() } returns "blake2b"
        every { "0x35456472".extrinsicHash() } returns "blake2b"
        mockkObject(OptionsProvider)
        given(userRepository.getCurSoraAccount()).willReturn(soraAccount)
        interactor = WalletInteractorImpl(
            assetsRepository,
            walletRepository,
            transactionHistoryRepository,
            userRepository,
            credentialsRepository,
            runtimeManager,
        )
    }

    @Test
    fun `needs migration`() = runTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        given(walletRepository.needsMigration("abcdef")).willReturn(true)
        given(userRepository.saveNeedsMigration(true, soraAccount)).willReturn(Unit)
        val result = interactor.needsMigration()
        assertEquals(true, result)
    }

    @Test
    fun `a submitted claim is checked for the current account`() = runTest {
        val outcome = jp.co.soramitsu.common.domain.TransactionOutcome.Waiting(
            txHash = "0xhash",
            acceptedByNode = true,
        )
        given(walletRepository.checkSubmittedClaim("address", "0xhash")).willReturn(outcome)

        assertEquals(outcome, interactor.checkSubmittedClaim("0xhash"))
    }

    @Test
    fun `find other users`() = runTest {
        given(runtimeManager.isAddressOk(anyString())).willReturn(true)
        given(
            transactionHistoryRepository.getContacts(
                soraAccount.substrateAddress,
                "use",
            )
        ).willReturn(
            setOf(
                "contact1",
                "contact2"
            )
        )
        assertEquals(accountList(), interactor.getContacts("use"))
    }

    @Test
    fun `migrate extrinsic`() = runTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        given(walletRepository.needsMigration(irohaData.address)).willReturn(true)
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        given(credentialsRepository.retrieveKeyPair(soraAccount)).willReturn(kp)
        given(
            walletRepository.migrate(
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyString(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "", "")
        )
        val br = BlockResponse(
            "justification",
            BlockEntry("header", listOf("0x112323345", "0x35456472"))
        )
        assertEquals(TransactionOutcome.Confirmed("", ""), interactor.migrate())
        verify(walletRepository).needsMigration(irohaData.address)
        verify(walletRepository).migrate(
            irohaData.address,
            irohaData.publicKey,
            irohaData.claimSignature,
            kp,
            soraAccount.substrateAddress,
        )
        assertEquals(true, kp.privateKey.all { it == 0.toByte() })
        assertEquals(true, kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `migrate rechecks authoritative account state before loading the signing key`() = runTest {
        given(credentialsRepository.getIrohaData(soraAccount)).willReturn(irohaData)
        given(walletRepository.needsMigration(irohaData.address)).willReturn(false)

        // Nothing to send: the network already has the claim.
        assertEquals(null, interactor.migrate())

        verify(userRepository).saveNeedsMigration(false, soraAccount)
        verify(credentialsRepository, never()).retrieveKeyPair(soraAccount)
        verify(walletRepository, never()).migrate(
            anyString(),
            anyString(),
            anyString(),
            any(),
            anyString(),
        )
    }

    private fun accountList() = listOf(
        "use", "contact1", "contact2",
    )

    private fun assetList() = listOf(
        Asset(oneToken(), true, 1, TestAssets.balance(BigDecimal.ONE), true),
    )

    private fun oneToken() = Token(
        "token_id",
        "token name",
        "token symbol",
        18,
        true,
        null,
        null,
        null,
        null,
    )
}
