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

package jp.co.soramitsu.feature_assets_impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBase
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicRefusedByNodeException
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknownException
import jp.co.soramitsu.sora.substrate.substrate.Sora2NodeRefusal
import jp.co.soramitsu.sora.substrate.substrate.extrinsicHash
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
            interactor.calcTransactionFee(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                expectedWalletId = "address",
            )
        )
    }

    @Test
    fun `calc transaction fee rejects a different selected wallet`() = runTest {
        coEvery { userRepository.getCurSoraAccount() } returns
            SoraAccount("different-address", "different-name")

        val error = try {
            interactor.calcTransactionFee(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                expectedWalletId = "address",
            )
            Assert.fail("Expected selected-wallet rejection")
            null
        } catch (error: Throwable) {
            error
        }

        Assert.assertEquals("SORA2_SELECTED_WALLET_CHANGED", error?.message)
        coVerify(exactly = 0) {
            assetsRepository.calcTransactionFee(any(), any(), any(), any())
        }
    }

    @Test
    fun `transfer wallet validator rejects selection changed after admission`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        val walletValidator = slot<suspend () -> Unit>()
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                capture(walletValidator),
            )
        } returns ExtrinsicSubmitStatus(true, "txhash", "blockhash")

        Assert.assertEquals(
            "txhash",
            interactor.observeTransfer(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                BigDecimal.ONE,
                expectedWalletId = "address",
            ).txHash,
        )
        coEvery { userRepository.getCurSoraAccount() } returns
            SoraAccount("different-address", "different-name")

        val error = try {
            walletValidator.captured()
            Assert.fail("Expected selected-wallet rejection")
            null
        } catch (error: Throwable) {
            error
        }

        Assert.assertEquals("SORA2_SELECTED_WALLET_CHANGED", error?.message)
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `a transfer that failed on chain is returned as failed with its reason`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns ExtrinsicSubmitStatus(
            success = false,
            txHash = "txhash",
            blockHash = "blockhash",
            failureDetail = "Assets.InsufficientBalance",
        )

        val outcome = interactor.observeTransfer(
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE,
            BigDecimal.ONE,
            expectedWalletId = "address",
        )

        Assert.assertEquals(
            TransactionOutcome.FailedOnChain(
                txHash = "txhash",
                blockHash = "blockhash",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "Assets.InsufficientBalance",
            ),
            outcome,
        )
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `a transfer the node refused is a failure and never a submitted hash`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        val transactionHash = "0x${"22".repeat(32)}"
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } throws ExtrinsicRefusedByNodeException(
            transactionHash = transactionHash,
            refusal = Sora2NodeRefusal(
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                code = 1010,
                message = "Inability to pay some fees",
            ),
            cause = IllegalStateException("SORA2_RUNTIME_RPC_REMOTE_ERROR"),
        )

        val outcome = interactor.observeTransfer(
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE,
            BigDecimal.ONE,
            expectedWalletId = "address",
        )

        Assert.assertEquals(
            TransactionOutcome.RefusedByNode(
                txHash = transactionHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees",
            ),
            outcome,
        )
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `observe transfer`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
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
                any(),
            )
        } returns txTransfer()
        every {
            transactionHistoryRepository.saveTransaction("address", any())
        } returns Unit
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns ExtrinsicSubmitStatus(true, "txhash", "")
        Assert.assertEquals(
            "txhash",
            interactor.observeTransfer(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                BigDecimal.ONE,
                expectedWalletId = "address",
            ).txHash
        )
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `confirmed transfer survives local history persistence failure`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
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
                any(),
            )
        } returns txTransfer()
        every {
            transactionHistoryRepository.saveTransaction("address", any())
        } throws IllegalStateException("local history unavailable")
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns ExtrinsicSubmitStatus(true, "txhash", "blockhash")

        Assert.assertEquals(
            "txhash",
            interactor.observeTransfer(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                BigDecimal.ONE,
                expectedWalletId = "address",
            ).txHash
        )
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `submission unknown transfer is persisted pending and returns exact hash`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        val transactionHash = "0x${"11".repeat(32)}"
        val submissionUnknown = ExtrinsicSubmissionUnknownException(
            transactionHash = transactionHash,
            cause = IllegalStateException("RPC connection lost"),
        )
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
                any(),
            )
        } returns txTransfer()
        every {
            transactionHistoryRepository.saveTransaction("address", any())
        } returns Unit
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } throws submissionUnknown

        Assert.assertEquals(
            transactionHash,
            interactor.observeTransfer(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                BigDecimal.ONE,
                expectedWalletId = "address",
            ).txHash,
        )

        verify {
            builder.buildTransfer(
                txHash = transactionHash,
                blockHash = null,
                fee = BigDecimal.ONE,
                status = TransactionStatus.PENDING,
                date = any(),
                amount = BigDecimal.ONE,
                peer = "to",
                type = TransactionTransferType.OUTGOING,
                token = TestTokens.xorToken,
            )
        }
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
    }

    @Test
    fun `unrelated transfer failure propagates after keypair zeroization`() = runTest {
        val kp = Sr25519Keypair(
            ByteArray(32) { 1 },
            ByteArray(32) { 2 },
            ByteArray(32) { 3 },
        )
        val submissionFailure = IllegalStateException("definitive local validation failure")
        coEvery { credentialsRepository.retrieveKeyPair(soraAccount) } returns kp
        coEvery {
            assetsRepository.observeTransfer(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } throws submissionFailure

        val error = try {
            interactor.observeTransfer(
                "to",
                TestTokens.xorToken,
                BigDecimal.ONE,
                BigDecimal.ONE,
                expectedWalletId = "address",
            )
            Assert.fail("Expected definitive failure")
            null
        } catch (error: Throwable) {
            error
        }

        Assert.assertTrue(error === submissionFailure)
        Assert.assertTrue(kp.privateKey.all { it == 0.toByte() })
        Assert.assertTrue(kp.nonce.all { it == 0.toByte() })
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
                xorChange = BigDecimal(1),
                networkFeeInXor = BigDecimal(1),
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
                xorChange = BigDecimal(1),
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
                xorChange = BigDecimal(1),
                networkFeeInXor = BigDecimal(1),
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
