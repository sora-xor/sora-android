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

package jp.co.soramitsu.feature_wallet_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.IrohaData
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.common.domain.KycRepository
import jp.co.soramitsu.sora.substrate.models.BlockEntry
import jp.co.soramitsu.sora.substrate.models.BlockResponse
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_shared.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.`when`
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.math.BigDecimal

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

    @Mock
    private lateinit var kycRepository: KycRepository

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
            kycRepository
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
    fun `find other users`() = runTest {
        given(runtimeManager.isAddressOk(anyString())).willReturn(true)
        given(transactionHistoryRepository.getContacts(anyString())).willReturn(
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
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
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
        assertEquals(true, interactor.migrate())
    }

    @Test
    fun `process qr called`() = runTest {
        val content = "substrate:notMyAddress:en:part4:part5"
        given(runtimeManager.isAddressOk("notMyAddress")).willReturn(true)
        given(assetsRepository.isWhitelistedToken("part5")).willReturn(true)
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!! == Triple("notMyAddress", "part5", BigDecimal.ZERO))
    }

    @Test
    fun `process qr called with wrong qr data`() = runTest {
        val content = "substrate:notMyAddress:en:tjj:qwe"
        given(runtimeManager.isAddressOk("notMyAddress")).willReturn(false)
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()!!
        assertTrue(
            exception is QrException && exception.kind == QrException.Kind.USER_NOT_FOUND
        )
    }

    @Test
    fun `process qr called with users qr data`() = runTest {
        val content = "substrate:address:en:tjj:qwe"
        val result = runCatching {
            interactor.processQr(content)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()!!
        assertTrue(
            exception is QrException && exception.kind == QrException.Kind.SENDING_TO_MYSELF
        )
    }

    @Test
    fun `poll while pending EXPECT polling is continued until status is not changed`() =
        runTest {
            `when`(walletRepository.getSoraCardInfo())
                .thenReturn(
                    SoraCardInformation(
                        id = "id",
                        accessToken = "accessToken",
                        refreshToken = "refreshToken",
                        accessTokenExpirationTime = System.currentTimeMillis() + 1_000,
                        kycStatus = "${SoraCardCommonVerification.Pending}"
                    )
                )
                .thenReturn(
                    SoraCardInformation(
                        id = "id",
                        accessToken = "accessToken",
                        refreshToken = "refreshToken",
                        accessTokenExpirationTime = System.currentTimeMillis() + 31_000,
                        kycStatus = "${SoraCardCommonVerification.Successful}"
                    )
                )

            given(kycRepository.getKycLastFinalStatus(any()))
                .willReturn(
                    Result.success(SoraCardCommonVerification.Successful)
                )

            interactor.pollSoraCardStatusIfPending().test(this) {
                advanceUntilIdle()
                assertEquals(
                    SoraCardCommonVerification.Successful.toString(),
                    awaitValue(0)
                )
            }

            verify(walletRepository, times(2))
                .getSoraCardInfo()
            verify(kycRepository, times(1))
                .getKycLastFinalStatus(any())
            verify(walletRepository, times(1))
                .updateSoraCardKycStatus(SoraCardCommonVerification.Successful.toString())
        }

    @Test
    fun `poll with exception EXPECT polling is continued until status is not changed`() =
        runTest {
            `when`(walletRepository.getSoraCardInfo())
                .thenReturn(
                    SoraCardInformation(
                        id = "id",
                        accessToken = "accessToken",
                        refreshToken = "refreshToken",
                        accessTokenExpirationTime = System.currentTimeMillis() + 1_000,
                        kycStatus = "${SoraCardCommonVerification.Pending}"
                    )
                )
                .thenReturn(
                    SoraCardInformation(
                        id = "id",
                        accessToken = "accessToken",
                        refreshToken = "refreshToken",
                        accessTokenExpirationTime = System.currentTimeMillis() + 1_500,
                        kycStatus = "${SoraCardCommonVerification.Pending}"
                    )
                )
                .thenReturn(
                    SoraCardInformation(
                        id = "id",
                        accessToken = "accessToken",
                        refreshToken = "refreshToken",
                        accessTokenExpirationTime = System.currentTimeMillis() + 31_000,
                        kycStatus = "${SoraCardCommonVerification.Successful}"
                    )
                )

            `when`(kycRepository.getKycLastFinalStatus(any()))
                .thenReturn(
                    Result.failure(RuntimeException())
                )
                .thenReturn(
                    Result.success(SoraCardCommonVerification.Successful)
                )

            interactor.pollSoraCardStatusIfPending().test(this) {
                advanceUntilIdle()
                assertEquals(
                    SoraCardCommonVerification.Successful.toString(),
                    awaitValue(0)
                )
            }

            verify(walletRepository, times(3))
                .getSoraCardInfo()
            verify(kycRepository, times(2))
                .getKycLastFinalStatus(any())
            verify(walletRepository, times(1))
                .updateSoraCardKycStatus(SoraCardCommonVerification.Successful.toString())
        }

    private fun accountList() = listOf(
        "use","contact1","contact2",
    )

    private fun assetList() = listOf(
        Asset(oneToken(), true, 1, assetBalance(), true),
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

    private fun assetBalance() = AssetBalance(
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE
    )
}
