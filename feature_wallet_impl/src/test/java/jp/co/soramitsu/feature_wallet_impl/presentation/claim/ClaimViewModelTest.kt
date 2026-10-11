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

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ClaimViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var router: WalletRouter

    @Mock
    private lateinit var cm: CoroutineManager

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var viewModel: ClaimViewModel

    @Before
    fun setUp() {
        given(walletInteractor.observeMigrationStatus()).willReturn(flow { emit(MigrationStatus.SUCCESS) })
        viewModel = ClaimViewModel(
            router,
            walletInteractor,
            cm,
            resourceManager,
        )
    }

    @Test
    fun `check init`() = runTest {
        advanceUntilIdle()
        verify(router).popBackStackFragment()
    }

    @Test
    fun `contacts us click`() {
        val c = "support@sora.org"
        viewModel.contactsUsClicked()
        viewModel.openSendEmailEvent.observeForever {
            assertEquals(c, it)
        }
    }

    @Test
    fun `next click`() = runTest {
        given(walletInteractor.needsMigration()).willReturn(false)
        viewModel.checkMigrationIsAlreadyFinished()
        advanceUntilIdle()
        verify(router, times(2)).popBackStackFragment()
    }

    // --- audit A5: the legacy claim tells the truth about every outcome ----------------------

    private val txHash = "0x" + "ab".repeat(32)

    private suspend fun TestScope.claimAnswers(answer: () -> TransactionOutcome?) {
        given(cm.io).willReturn(checkNotNull(coroutineContext[CoroutineDispatcher]))
        given(walletInteractor.migrate()).willAnswer { answer() }
    }

    private fun stubOutcomeTexts() {
        listOf(
            R.string.tx_outcome_waiting_title,
            R.string.tx_outcome_claim_waiting,
            R.string.tx_outcome_refused_title,
            R.string.tx_outcome_note_refused_fee,
            R.string.tx_outcome_not_sent_title,
            R.string.tx_outcome_not_sent_network,
        ).forEach { id ->
            given(resourceManager.getString(id)).willReturn("text-$id")
        }
    }

    @Test
    fun `a confirmed claim is stored as done`() = runTest {
        claimAnswers { TransactionOutcome.Confirmed(txHash, "0xblock") }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor).saveMigrationStatus(MigrationStatus.SUCCESS)
    }

    @Test
    fun `a claim the network already has is stored as done without sending`() = runTest {
        claimAnswers { null }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor).saveMigrationStatus(MigrationStatus.SUCCESS)
    }

    @Test
    fun `a claim that is still waiting is said to be waiting and never stored as failed`() =
        runTest {
            stubOutcomeTexts()
            // What a failed status read or an unfinished finality wait gives after a broadcast.
            claimAnswers { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            viewModel.nextButtonClicked()
            advanceUntilIdle()

            // Before the fix: stored as FAILED and "Transition of assets failed" on every visit.
            verify(walletInteractor, never()).saveMigrationStatus(MigrationStatus.FAILED)
            verify(walletInteractor, never()).saveMigrationStatus(MigrationStatus.SUCCESS)
            assertEquals(
                "text-${R.string.tx_outcome_waiting_title}" to
                    "text-${R.string.tx_outcome_claim_waiting}",
                viewModel.alertDialogLiveData.value,
            )
            assertEquals(false, viewModel.claimScreenState.value?.loading)
        }

    @Test
    fun `a second claim while the first is unresolved is said to be waiting`() = runTest {
        stubOutcomeTexts()
        claimAnswers { throw IllegalStateException("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED") }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor, never()).saveMigrationStatus(MigrationStatus.FAILED)
        assertEquals(
            "text-${R.string.tx_outcome_waiting_title}" to
                "text-${R.string.tx_outcome_claim_waiting}",
            viewModel.alertDialogLiveData.value,
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim that failed on chain is stored as failed`() = runTest {
        claimAnswers {
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
            )
        }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor).saveMigrationStatus(MigrationStatus.FAILED)
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim the node refused is a failure with its reason and is not stored`() = runTest {
        stubOutcomeTexts()
        claimAnswers {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = null,
            )
        }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor, never()).saveMigrationStatus(any())
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            viewModel.alertDialogLiveData.value,
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim that could not be prepared says nothing was sent and is not stored`() = runTest {
        stubOutcomeTexts()
        claimAnswers { throw IOException("BOUNDED_HTTP_IO") }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        verify(walletInteractor, never()).saveMigrationStatus(any())
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_network}",
            viewModel.alertDialogLiveData.value,
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `an unreachable network while checking the claim does not crash the screen`() = runTest {
        given(walletInteractor.needsMigration()).willAnswer { throw IOException("offline") }

        viewModel.checkMigrationIsAlreadyFinished()
        advanceUntilIdle()

        // Only the init path (status SUCCESS) popped the screen.
        verify(router, times(1)).popBackStackFragment()
    }
}
