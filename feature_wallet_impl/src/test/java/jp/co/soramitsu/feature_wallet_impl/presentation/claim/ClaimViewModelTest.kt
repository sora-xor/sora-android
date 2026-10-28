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

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ClaimViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    /**
     * The interactor boundary. What it stores as the migration status comes back through the
     * status flow, as in production, so the screen closes through the view model's own collector.
     */
    private class FakeWalletInteractor : WalletInteractor {
        val status = MutableSharedFlow<MigrationStatus>(replay = 1)
        val saved = mutableListOf<MigrationStatus>()

        var migrateAnswer: suspend () -> TransactionOutcome? = { error("migrate is not expected") }
        var migrateCalls = 0

        /** Answers in order; the last one repeats. True: the claim is still outstanding. */
        var needsMigrationAnswers: List<() -> Boolean> = listOf({ true })
        var needsMigrationCalls = 0

        /** Answers in order; the last one repeats. */
        var submittedClaimAnswers: List<(String?) -> TransactionOutcome?> = listOf({ null })
        val submittedClaimAskedFor = mutableListOf<String?>()

        override suspend fun saveMigrationStatus(migrationStatus: MigrationStatus) {
            saved += migrationStatus
            status.emit(migrationStatus)
        }

        override fun observeMigrationStatus(): Flow<MigrationStatus> = status

        override suspend fun needsMigration(): Boolean {
            val answer =
                needsMigrationAnswers[minOf(needsMigrationCalls, needsMigrationAnswers.lastIndex)]
            needsMigrationCalls += 1
            return answer()
        }

        override suspend fun migrate(): TransactionOutcome? {
            migrateCalls += 1
            return migrateAnswer.invoke()
        }

        override suspend fun checkSubmittedClaim(txHash: String?): TransactionOutcome? {
            val answer = submittedClaimAnswers[
                minOf(submittedClaimAskedFor.size, submittedClaimAnswers.lastIndex)
            ]
            submittedClaimAskedFor += txHash
            return answer(txHash)
        }

        override suspend fun getFeeToken(): Token = error("not used")
        override suspend fun getContacts(query: String): List<String>? = error("not used")
        override suspend fun getSoraAccounts(): List<SoraAccount> = error("not used")
    }

    private val walletInteractor = FakeWalletInteractor()
    private val router: WalletRouter = Mockito.mock(WalletRouter::class.java)
    private val cm: CoroutineManager = Mockito.mock(CoroutineManager::class.java)

    // Every text is "text-<resource id>", so a test names the string it expects.
    private val resourceManager: ResourceManager =
        Mockito.mock(ResourceManager::class.java) { invocation ->
            if (invocation.method.name == "getString") "text-${invocation.arguments[0]}" else null
        }

    private lateinit var viewModel: ClaimViewModel

    private val txHash = "0x" + "ab".repeat(32)
    private val waiting = TransactionOutcome.Waiting(txHash, acceptedByNode = true)
    private val expired = TransactionOutcome.FailedOnChain(
        txHash = txHash,
        blockHash = null,
        reason = TransactionChainFailure.NOT_INCLUDED,
    )

    @Before
    fun setUp() {
        viewModel = ClaimViewModel(
            router,
            walletInteractor,
            cm,
            resourceManager,
        )
    }

    /** Network and storage work runs on the test's own dispatcher. */
    private fun TestScope.useTestDispatcher() {
        Mockito.`when`(cm.io).thenReturn(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
    }

    private fun TestScope.claimAnswers(answer: suspend () -> TransactionOutcome?) {
        useTestDispatcher()
        walletInteractor.migrateAnswer = answer
    }

    private fun text(id: Int) = "text-$id"

    private fun dialog() = viewModel.alertDialogLiveData.value

    // --- closing the screen: only while the claim screen itself is in front -------------------

    @Test
    fun `a claim that is done closes the claim screen when it is in front`() = runTest {
        useTestDispatcher()
        walletInteractor.status.emit(MigrationStatus.SUCCESS)
        advanceUntilIdle()
        // Not in front yet (the fragment has not resumed): nothing is popped.
        verify(router, never()).closeClaimScreen()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        verify(router).closeClaimScreen()
        // The blind pop, which removes whatever screen is on top, is never used.
        verify(router, never()).popBackStackFragment()
        assertEquals(0, walletInteractor.needsMigrationCalls)
    }

    @Test
    fun `a claim found done while another screen is on top does not navigate`() = runTest {
        claimAnswers { waiting }
        walletInteractor.submittedClaimAnswers = listOf({ null }, { waiting })
        viewModel.onScreenResumed()
        runCurrent()
        viewModel.nextButtonClicked()
        runCurrent()
        assertEquals(text(R.string.tx_outcome_waiting_title), dialog()?.first)

        // The app was in the background for more than five minutes and the PIN lock is now on
        // top of the claim screen: the claim fragment is paused, its view model lives on.
        viewModel.onScreenPaused()
        // The network now has the claim.
        walletInteractor.needsMigrationAnswers = listOf({ false })
        advanceTimeBy(20 * 60_000L)
        // The follow timer does not run while the screen is not in front ...
        assertEquals(1, walletInteractor.needsMigrationCalls)
        // ... and a success that arrives by any other path (here: the status is stored) must
        // not pop either. Before the fix this popped the PIN lock, and the claim screen then
        // popped itself: the wallet was open with no PIN or biometric entered.
        walletInteractor.saveMigrationStatus(MigrationStatus.SUCCESS)
        advanceUntilIdle()
        verify(router, never()).closeClaimScreen()
        verify(router, never()).popBackStackFragment()

        // The PIN was entered; the claim screen is in front again and closes itself.
        viewModel.onScreenResumed()
        advanceUntilIdle()
        verify(router, times(1)).closeClaimScreen()
        verify(router, never()).popBackStackFragment()
    }

    @Test
    fun `a submission that is confirmed while the screen is covered closes it only on return`() =
        runTest {
            val answer = CompletableDeferred<TransactionOutcome?>()
            claimAnswers { answer.await() }
            viewModel.onScreenResumed()
            runCurrent()
            viewModel.nextButtonClicked()
            runCurrent()

            // Another screen comes on top while the submission is still waiting for finality.
            viewModel.onScreenPaused()
            answer.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
            advanceUntilIdle()

            assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
            verify(router, never()).closeClaimScreen()
            verify(router, never()).popBackStackFragment()

            viewModel.onScreenResumed()
            advanceUntilIdle()
            verify(router, times(1)).closeClaimScreen()
        }

    @Test
    fun `returning to the screen asks the network once and closes when the claim is there`() =
        runTest {
            useTestDispatcher()
            walletInteractor.needsMigrationAnswers = listOf({ false })

            viewModel.onScreenResumed()
            advanceUntilIdle()

            verify(router).closeClaimScreen()
            assertEquals(1, walletInteractor.needsMigrationCalls)
            // As before: found done on a return to the screen, nothing is stored.
            assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        }

    @Test
    fun `returning to the screen without a waiting claim asks once and stays`() = runTest {
        useTestDispatcher()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertEquals(1, walletInteractor.needsMigrationCalls)
        assertEquals(listOf<String?>(null), walletInteractor.submittedClaimAskedFor)
        verify(router, never()).closeClaimScreen()
        assertEquals(null, dialog())
    }

    @Test
    fun `an unreachable network while checking the claim does not crash the screen`() = runTest {
        useTestDispatcher()
        walletInteractor.needsMigrationAnswers = listOf({ throw IOException("offline") })
        walletInteractor.submittedClaimAnswers = listOf({ throw IOException("offline") })

        viewModel.onScreenResumed()
        advanceUntilIdle()

        verify(router, never()).closeClaimScreen()
        assertEquals(null, dialog())
    }

    @Test
    fun `contacts us click`() {
        val c = "support@sora.org"
        viewModel.contactsUsClicked()
        viewModel.openSendEmailEvent.observeForever {
            assertEquals(c, it)
        }
    }

    // --- audit A5: the legacy claim tells the truth about every outcome ----------------------

    @Test
    fun `a confirmed claim is stored as done`() = runTest {
        claimAnswers { TransactionOutcome.Confirmed(txHash, "0xblock") }
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
        verify(router).closeClaimScreen()
    }

    @Test
    fun `a claim the network already has is stored as done without sending`() = runTest {
        claimAnswers { null }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
    }

    @Test
    fun `a claim that is still waiting is said to be waiting and never stored as failed`() =
        runTest {
            // What a failed status read or an unfinished finality wait gives after a broadcast.
            claimAnswers { waiting }
            // The claim stays outstanding and unresolved for as long as this screen keeps asking.
            walletInteractor.submittedClaimAnswers = listOf({ null }, { waiting })
            viewModel.onScreenResumed()
            runCurrent()

            viewModel.nextButtonClicked()
            advanceUntilIdle()

            // Before the first fix: stored as FAILED and "Transition of assets failed" on every
            // visit.
            assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
            assertEquals(
                text(R.string.tx_outcome_waiting_title) to text(R.string.tx_outcome_claim_waiting),
                dialog(),
            )
            assertEquals(false, viewModel.claimScreenState.value?.loading)
            // One check when the screen came to the front, then 48 while the transaction could
            // still be included. Then it stopped, and it never signed or sent anything again.
            assertEquals(1 + 48, walletInteractor.needsMigrationCalls)
            assertEquals(1, walletInteractor.migrateCalls)
            // After the tap it asked about the claim it had sent, by its hash.
            assertEquals(
                List(48) { txHash },
                walletInteractor.submittedClaimAskedFor.drop(1),
            )
            verify(router, never()).closeClaimScreen()
        }

    @Test
    fun `a claim whose submission may not have left the phone is not said to be submitted`() =
        runTest {
            // The connection dropped during the one submission request: no answer from the node.
            claimAnswers { TransactionOutcome.Waiting(txHash, acceptedByNode = false) }

            viewModel.nextButtonClicked()
            advanceUntilIdle()

            // Before: "Your claim was submitted and the network has not confirmed it yet".
            assertEquals(
                text(R.string.tx_outcome_waiting_title) to
                    text(R.string.tx_outcome_claim_uncertain),
                dialog(),
            )
            assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        }

    @Test
    fun `a waiting claim closes the screen by itself once the network has it`() = runTest {
        claimAnswers { waiting }
        walletInteractor.submittedClaimAnswers = listOf({ null }, { waiting })
        // On resume and at two checks the claim is outstanding; then the network has it.
        walletInteractor.needsMigrationAnswers = listOf({ true }, { true }, { true }, { false })
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(4, walletInteractor.needsMigrationCalls)
        assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
        assertEquals(1, walletInteractor.migrateCalls)
        verify(router).closeClaimScreen()
    }

    @Test
    fun `a failed read while following a waiting claim is asked again`() = runTest {
        claimAnswers { waiting }
        walletInteractor.submittedClaimAnswers = listOf({ null }, { throw IOException("offline") })
        walletInteractor.needsMigrationAnswers =
            listOf({ true }, { throw IOException("offline") }, { false })
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(3, walletInteractor.needsMigrationCalls)
        assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
    }

    @Test
    fun `a waiting claim that expired is reported and can be submitted again`() = runTest {
        claimAnswers { waiting }
        // On resume nothing is waiting. After the tap: waiting twice, then its validity window
        // ended without the claim being included.
        walletInteractor.submittedClaimAnswers =
            listOf({ null }, { waiting }, { waiting }, { expired })
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        // Before: the loop asked only "does the network have the claim", got "no" 48 times and
        // ended in silence, although the dialog had promised that the screen continues by
        // itself. Nothing told the person that the claim did not go through.
        assertEquals(
            text(R.string.tx_outcome_failed_title) to text(R.string.tx_outcome_note_not_included),
            dialog(),
        )
        // Nothing happened on chain, so nothing is stored and Submit is available again.
        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(false, viewModel.claimScreenState.value?.loading)
        // It stopped asking as soon as the result was final.
        assertEquals(1 + 3, walletInteractor.needsMigrationCalls)
        assertEquals(1, walletInteractor.migrateCalls)
    }

    @Test
    fun `a waiting claim that failed on chain is reported as failed`() = runTest {
        claimAnswers { waiting }
        walletInteractor.submittedClaimAnswers = listOf(
            { null },
            { waiting },
            {
                TransactionOutcome.FailedOnChain(
                    txHash = txHash,
                    blockHash = "0xblock",
                    reason = TransactionChainFailure.EXECUTION_FAILED,
                )
            },
        )
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(listOf(MigrationStatus.FAILED), walletInteractor.saved)
        assertEquals(1 + 2, walletInteractor.needsMigrationCalls)
        verify(router, never()).closeClaimScreen()
    }

    @Test
    fun `a waiting claim the journal shows confirmed closes the screen`() = runTest {
        claimAnswers { waiting }
        walletInteractor.submittedClaimAnswers =
            listOf({ null }, { TransactionOutcome.Confirmed(txHash, "0xblock") })
        // The claim state itself cannot be read (the node is unreachable for that call).
        walletInteractor.needsMigrationAnswers = listOf({ throw IOException("offline") })
        viewModel.onScreenResumed()
        runCurrent()

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(listOf(MigrationStatus.SUCCESS), walletInteractor.saved)
        verify(router).closeClaimScreen()
    }

    @Test
    fun `a claim sent before the screen was opened again is followed`() = runTest {
        useTestDispatcher()
        // A new claim screen after the PIN lock: this view model sent nothing itself, but the
        // journal holds the claim sent earlier, still waiting, and later expired.
        walletInteractor.submittedClaimAnswers = listOf({ waiting }, { waiting }, { expired })

        viewModel.onScreenResumed()
        runCurrent()

        // Before: a fresh claim screen that said nothing and never changed.
        assertEquals(
            text(R.string.tx_outcome_waiting_title) to text(R.string.tx_outcome_claim_waiting),
            dialog(),
        )

        advanceUntilIdle()

        assertEquals(
            text(R.string.tx_outcome_failed_title) to text(R.string.tx_outcome_note_not_included),
            dialog(),
        )
        // First asked without a hash (the newest claim of the account), then by its hash.
        assertEquals(listOf(null, txHash, txHash), walletInteractor.submittedClaimAskedFor)
        assertEquals(0, walletInteractor.migrateCalls)
        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
    }

    @Test
    fun `the final result of an old claim is not announced again on a fresh screen`() = runTest {
        useTestDispatcher()
        // The newest claim this device recorded for the account failed days ago.
        walletInteractor.submittedClaimAnswers = listOf({
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
            )
        })

        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(null, dialog())
        assertEquals(1, walletInteractor.needsMigrationCalls)
    }

    @Test
    fun `a claim that was never included is explained and not stored as failed`() = runTest {
        claimAnswers { expired }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        // Nothing happened on chain: "Transition of assets failed" on every visit would be wrong.
        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(
            text(R.string.tx_outcome_failed_title) to text(R.string.tx_outcome_note_not_included),
            dialog(),
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim blocked by an unresolved earlier transaction says nothing was sent`() = runTest {
        claimAnswers { throw IllegalStateException("SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED") }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(
            text(R.string.tx_outcome_not_sent_title) to text(R.string.tx_outcome_claim_blocked),
            dialog(),
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

        assertEquals(listOf(MigrationStatus.FAILED), walletInteractor.saved)
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim the node refused is a failure with its reason and is not stored`() = runTest {
        claimAnswers {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = null,
            )
        }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(
            text(R.string.tx_outcome_refused_title) to text(R.string.tx_outcome_note_refused_fee),
            dialog(),
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }

    @Test
    fun `a claim that could not be prepared says nothing was sent and is not stored`() = runTest {
        claimAnswers { throw IOException("BOUNDED_HTTP_IO") }

        viewModel.nextButtonClicked()
        advanceUntilIdle()

        assertEquals(emptyList<MigrationStatus>(), walletInteractor.saved)
        assertEquals(
            text(R.string.tx_outcome_not_sent_title) to text(R.string.tx_outcome_not_sent_network),
            dialog(),
        )
        assertEquals(false, viewModel.claimScreenState.value?.loading)
    }
}
