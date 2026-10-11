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

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.transaction.TransactionOutcomeTexts
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ClaimViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val coroutineManager: CoroutineManager,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    private val _openSendEmailEvent = SingleLiveEvent<String>()
    val openSendEmailEvent: LiveData<String> = _openSendEmailEvent

    private val _claimScreenState = SingleLiveEvent<ClaimState>()
    val claimScreenState: LiveData<ClaimState> = _claimScreenState

    /** The claim is known to be on the network; the screen closes when it is in front. */
    private var claimDone = false

    /** A tapped submission is in flight; it reports its own result. */
    private var submitting = false

    /** The claim of this account that this device sent and that has no final status yet. */
    private var waitingClaimHash: String? = null

    private var followJob: Job? = null

    init {
        viewModelScope.launch {
            tryCatch {
                walletInteractor.observeMigrationStatus().collectLatest {
                    when (it) {
                        MigrationStatus.NOT_INITIATED -> {
                        }

                        MigrationStatus.FAILED -> onError(R.string.claim_error_title_v1)
                        MigrationStatus.SUCCESS -> {
                            claimDone = true
                            closeWhenInFront()
                        }
                    }
                }
            }
        }

        _claimScreenState.value = ClaimState(false)
    }

    /**
     * The claim screen came to the front. If the claim is already known to be done the screen
     * closes; otherwise the network is asked, and a claim this device sent earlier and that is
     * still waiting is followed. Nothing is signed or sent from here.
     */
    override fun onScreenResumed() {
        super.onScreenResumed()
        if (claimDone) {
            closeWhenInFront()
            return
        }
        if (!submitting) follow(checkAtOnce = true)
    }

    /**
     * The claim screen left the front: the app went to the background, or another screen (the
     * PIN lock) is on top. Nothing is followed and nothing is closed until it is back.
     */
    override fun onScreenPaused() {
        super.onScreenPaused()
        followJob?.cancel()
        followJob = null
    }

    /**
     * Closes the claim screen only while it is the screen in front. Everything that finds the
     * claim done (the tapped submission, the follow timer, a late network answer) ends here, so
     * none of them can pop a screen that was put on top in the meantime. After a pause that
     * screen is the PIN lock, and popping it would open the wallet without the PIN. When the
     * claim screen is not in front, [onScreenResumed] closes it as soon as it is.
     */
    private fun closeWhenInFront() {
        if (claimDone && screenInFront) router.closeClaimScreen()
    }

    fun contactsUsClicked() {
        _openSendEmailEvent.postValue(OptionsProvider.email)
    }

    /**
     * Submits the claim and shows exactly what is known afterwards. Only a claim the network
     * confirmed (or already had) is stored as done, and only one that was included and failed
     * there as failed. A claim that is still waiting is said to be waiting, is never stored as
     * failed, and this screen then follows it and reports its result. While an earlier
     * transaction of the account is unresolved the claim gate sends nothing, and the screen says
     * so. The progress mark is cleared on every path that keeps the screen open.
     */
    fun nextButtonClicked() {
        if (submitting) return
        submitting = true
        followJob?.cancel()
        followJob = null
        viewModelScope.launch {
            setLoading(true)
            var outcome: TransactionOutcome? = null
            var notSent: Throwable? = null
            try {
                outcome = withContext(coroutineManager.io) { walletInteractor.migrate() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                notSent = t
            } finally {
                submitting = false
            }
            FirebaseWrapper.log("SORA migration done ${outcome?.let { it::class.simpleName }}")
            when {
                notSent != null && notSent.isEarlierTransactionUnresolved() -> {
                    // Nothing was sent now. The earlier transaction may be this claim or any
                    // other one of this account, so the text does not say which.
                    setLoading(false)
                    alertDialogLiveData.setValue(
                        resourceManager.getString(R.string.tx_outcome_not_sent_title) to
                            resourceManager.getString(R.string.tx_outcome_claim_blocked)
                    )
                    // If it is a claim, this screen follows it and reports its result.
                    if (screenInFront) follow(checkAtOnce = true)
                }
                notSent != null -> {
                    setLoading(false)
                    showTransactionNotSent(resourceManager, notSent)
                }
                outcome == null || outcome is TransactionOutcome.Confirmed ->
                    saveStatus(MigrationStatus.SUCCESS)
                outcome is TransactionOutcome.Waiting -> {
                    setLoading(false)
                    waitingClaimHash = outcome.txHash
                    showClaimWaiting(outcome.acceptedByNode)
                    if (screenInFront) follow(checkAtOnce = false)
                }
                outcome is TransactionOutcome.FailedOnChain -> {
                    setLoading(false)
                    showClaimFailed(outcome)
                }
                outcome is TransactionOutcome.RefusedByNode -> {
                    setLoading(false)
                    showTransactionRefused(resourceManager, outcome)
                }
            }
        }
    }

    private suspend fun saveStatus(status: MigrationStatus) {
        tryCatch {
            withContext(coroutineManager.io) { walletInteractor.saveMigrationStatus(status) }
        }
    }

    private fun setLoading(loading: Boolean) {
        _claimScreenState.value?.let {
            _claimScreenState.value = it.copy(loading = loading)
        }
    }

    /**
     * The claim screen cannot be left without closing the app, so it must not sit unchanged
     * while something it showed is being decided elsewhere. While it is in front, and only then,
     * it asks at intervals:
     *
     * 1. whether the network has this account's claim (then the screen closes), and
     * 2. what became of the claim this device sent, if one is still waiting: confirmed closes the
     *    screen; failed on chain, or expired without being included, is said so and Submit can
     *    be used again. A waiting claim found after the screen was opened anew (for example
     *    after the PIN lock) is followed in the same way.
     *
     * Nothing is signed or sent from here. A read that fails (for example while offline) is
     * simply repeated at the next interval. Without a waiting claim there is one check, as on
     * every return to this screen before. The number of checks per visit is bounded; the next
     * return to the screen, or Submit, starts it again.
     */
    private fun follow(checkAtOnce: Boolean) {
        followJob?.cancel()
        followJob = viewModelScope.launch {
            repeat(WAITING_CLAIM_CHECKS) { check ->
                if (check > 0 || !checkAtOnce) delay(WAITING_CLAIM_CHECK_INTERVAL_MILLIS)
                val claimed = readOrNull { !walletInteractor.needsMigration() } ?: false
                if (claimed) {
                    if (waitingClaimHash != null) {
                        waitingClaimHash = null
                        saveStatus(MigrationStatus.SUCCESS)
                    } else {
                        // Found done on a return to the screen (another device may have
                        // claimed): the screen closes, as before, without storing anything.
                        claimDone = true
                        closeWhenInFront()
                    }
                    return@launch
                }
                val followed = waitingClaimHash
                when (val known = readOrNull { walletInteractor.checkSubmittedClaim(followed) }) {
                    is TransactionOutcome.Waiting -> if (followed == null) {
                        // A claim sent before this screen was opened is still waiting.
                        waitingClaimHash = known.txHash
                        showClaimWaiting(known.acceptedByNode)
                    }
                    is TransactionOutcome.Confirmed -> if (followed != null) {
                        waitingClaimHash = null
                        saveStatus(MigrationStatus.SUCCESS)
                        return@launch
                    }
                    is TransactionOutcome.FailedOnChain -> if (followed != null) {
                        waitingClaimHash = null
                        showClaimFailed(known)
                        return@launch
                    }
                    // A refusal was shown when it happened; an unreadable status is asked again.
                    is TransactionOutcome.RefusedByNode, null -> Unit
                }
                if (waitingClaimHash == null) return@launch
            }
        }
    }

    /** A failed read is not a result: null, and the caller asks again at the next interval. */
    private suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
        withContext(coroutineManager.io) { read() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    /**
     * The network received the claim, or the phone never got its answer: the two are said
     * differently, because only the first is known to be on its way.
     */
    private fun showClaimWaiting(acceptedByNode: Boolean) {
        alertDialogLiveData.setValue(
            resourceManager.getString(R.string.tx_outcome_waiting_title) to
                resourceManager.getString(
                    if (acceptedByNode) {
                        R.string.tx_outcome_claim_waiting
                    } else {
                        R.string.tx_outcome_claim_uncertain
                    }
                )
        )
    }

    /**
     * Only a claim that was included and failed there is stored as failed. One that was never
     * included changed nothing on chain, so nothing is stored and it can be submitted again.
     */
    private suspend fun showClaimFailed(outcome: TransactionOutcome.FailedOnChain) {
        if (outcome.reason == TransactionChainFailure.EXECUTION_FAILED) {
            saveStatus(MigrationStatus.FAILED)
        } else {
            val text = TransactionOutcomeTexts.describe(resourceManager, outcome)
            alertDialogLiveData.setValue(text.title to text.message)
        }
    }

    /**
     * The claim gate refuses to sign a claim while an earlier transaction of this account has no
     * final status yet and may still reach the chain (a waiting claim or another waiting
     * transaction). One the node explicitly refused does not count: after "the network was too
     * busy, please try again" Submit works at once.
     */
    private fun Throwable.isEarlierTransactionUnresolved(): Boolean {
        var current: Throwable? = this
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current.message == EARLIER_CLAIM_UNRESOLVED) return true
            current = current.cause?.takeIf { it !== current }
            depth += 1
        }
        return false
    }

    private companion object {
        const val EARLIER_CLAIM_UNRESOLVED = "SORA2_LEGACY_MIGRATION_ALREADY_UNRESOLVED"
        const val MAX_CAUSE_DEPTH = 16

        /**
         * Ten seconds apart for eight minutes: a submitted transaction can be included for 64
         * blocks (about six and a half minutes), after which it can no longer change anything.
         */
        const val WAITING_CLAIM_CHECK_INTERVAL_MILLIS = 10_000L
        const val WAITING_CLAIM_CHECKS = 48
    }
}
