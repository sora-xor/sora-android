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

package jp.co.soramitsu.feature_assets_impl.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.mockkStatic
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@ExperimentalCoroutinesApi
class AssetsInteractorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var assetsRepository: AssetsRepository

    @MockK
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var credentialsRepository: CredentialsRepository

    @MockK
    private lateinit var builder: TransactionBuilder

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    private lateinit var interactor: AssetsInteractor

    private val soraAccount = SoraAccount("address", "name")

    @Before
    fun setUp() = runTest {
        mockkStatic(String::extrinsicHash)
        every { "0x112323345".extrinsicHash() } returns "blake2b"
        every { "0x35456472".extrinsicHash() } returns "blake2b"
        mockkObject(OptionsProvider)
        coEvery { userRepository.getCurSoraAccount() } returns soraAccount
        interactor = AssetsInteractorImpl(
            assetsRepository = assetsRepository,
            credentialsRepository = credentialsRepository,
            coroutineManager = coroutineManager,
            transactionBuilder = builder,
            transactionHistoryRepository = transactionHistoryRepository,
            userRepository = userRepository,
        )
    }

    @Test
    fun `get assets`() = runTest {
        coEvery { assetsRepository.getAssetsFavorite("address") } returns assetList()
        Assert.assertEquals(assetList(), interactor.getVisibleAssets())
    }

    @Test
    fun `just transfer`() = runTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.transfer(
                kp,
                "address",
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE
            )
        } returns Result.success("")
        Assert.assertEquals(
            Result.success(""),
            interactor.transfer("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `calc transaction fee`() = runTest {
        coEvery {
            assetsRepository.calcTransactionFee(
                "address",
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE
            )
        } returns BigDecimal.TEN
        Assert.assertEquals(
            BigDecimal.TEN,
            interactor.calcTransactionFee("to", TestTokens.xorToken, BigDecimal.ONE)
        )
    }

    @Test
    fun `observe transfer`() = runTest {
        val kp = Sr25519Keypair(ByteArray(32), ByteArray(32), ByteArray(32))
        every {
            builder.buildTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns txTransfer()
        every { transactionHistoryRepository.saveTransaction(any()) } returns Unit
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns ExtrinsicSubmitStatus(true, "txhash", "")
        Assert.assertEquals(
            "txhash",
            interactor.observeTransfer("to", TestTokens.xorToken, BigDecimal.ONE, BigDecimal.ONE)
        )
    }

    @Test
    fun `hide assets`() = runTest {
        val assets = listOf("id1", "id2")
        coEvery { assetsRepository.hideAssets(assets, soraAccount) } returns Unit
        Assert.assertEquals(Unit, interactor.tokenFavoriteOff(assets))
    }

    @Test
    fun `display assets`() = runTest {
        val assets = listOf("id1", "id2")
        coEvery { assetsRepository.displayAssets(assets, soraAccount) } returns Unit
        Assert.assertEquals(Unit, interactor.tokenFavoriteOn(assets))
    }

    @Test
    fun `update assets position`() = runTest {
        val assets = mapOf("id1" to 1, "id2" to 2)
        coEvery { assetsRepository.updateAssetPositions(assets, soraAccount) } returns Unit
        Assert.assertEquals(Unit, interactor.updateAssetPositions(assets))
    }

    @Test
    fun `get account id called`() = runTest {
        Assert.assertEquals(
            soraAccount.substrateAddress,
            interactor.getCurSoraAccount().substrateAddress
        )
    }

    @Test
    fun `CHECK isNotEnoughXorLeftAfterTransaction WHEN no xor token is supplied and balance equals network fee`() =
        runTest {
            coEvery { assetsRepository.getAsset(any(), any()) } returns TestAssets.xorAsset(
                BigDecimal.ONE
            )

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                primaryToken = oneToken(),
                primaryTokenAmount = BigDecimal(1),
                secondaryToken = oneToken(),
                secondaryTokenAmount = BigDecimal(1),
                networkFeeInXor = BigDecimal(1),
                isUnbonding = false
            )

            Assert.assertEquals(
                true,
                result
            )
        }

    @Test
    fun `CHECK isNotEnoughXorLeftAfterTransaction WHEN xor token is supplied and balance equals network fee`() =
        runTest {
            coEvery { assetsRepository.getAsset(any(), any()) } returns TestAssets.xorAsset(
                BigDecimal.ONE
            )

            val xorToken = Token(
                id = SubstrateOptionsProvider.feeAssetId,
                name = "",
                symbol = "xor",
                precision = 18,
                isHidable = false,
                iconFile = null,
                fiatPrice = null,
                fiatPriceChange = null,
                fiatSymbol = null
            )

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                primaryToken = xorToken,
                primaryTokenAmount = BigDecimal(1),
                secondaryToken = oneToken(),
                secondaryTokenAmount = BigDecimal(1),
                networkFeeInXor = BigDecimal(1)
            )

            Assert.assertEquals(
                true,
                result
            )
        }

    @Test
    fun `CHECK isNotEnoughXorLeftAfterTransaction WHEN xor token is produced and balance equals network fee`() =
        runTest {
            coEvery { assetsRepository.getAsset(any(), any()) } returns TestAssets.xorAsset(
                BigDecimal.ONE
            )

            val xorToken = Token(
                id = SubstrateOptionsProvider.feeAssetId,
                name = "",
                symbol = "xor",
                precision = 18,
                isHidable = false,
                iconFile = null,
                fiatPrice = null,
                fiatPriceChange = null,
                fiatSymbol = null
            )

            val result = interactor.isNotEnoughXorLeftAfterTransaction(
                primaryToken = oneToken(),
                primaryTokenAmount = BigDecimal(1),
                secondaryToken = xorToken,
                secondaryTokenAmount = BigDecimal(1),
                networkFeeInXor = BigDecimal(1),
                isUnbonding = false
            )

            Assert.assertEquals(true, result)
        }

    private fun txTransfer() = Transaction.Transfer(
        base = TransactionBase(
            txHash = "",
            blockHash = "",
            fee = BigDecimal.ZERO,
            status = TransactionStatus.COMMITTED,
            timestamp = 123123,
        ),
        amount = BigDecimal.ZERO,
        peer = "",
        transferType = TransactionTransferType.OUTGOING,
        token = TestTokens.xorToken,
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
