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
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.CancellationException
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

    init {
        viewModelScope.launch {
            tryCatch {
                walletInteractor.observeMigrationStatus().collectLatest {
                    when (it) {
                        MigrationStatus.NOT_INITIATED -> {
                        }

                        MigrationStatus.FAILED -> onError(R.string.claim_error_title_v1)
                        MigrationStatus.SUCCESS -> router.popBackStackFragment()
                    }
                }
            }
        }

        _claimScreenState.value = ClaimState(false)
    }

    fun checkMigrationIsAlreadyFinished() {
        viewModelScope.launch {
            // Asked from the network; while it cannot be reached the screen simply stays.
            tryCatch {
                if (!walletInteractor.needsMigration()) router.popBackStackFragment()
            }
        }
    }

    fun contactsUsClicked() {
        _openSendEmailEvent.postValue(OptionsProvider.email)
    }

    /**
     * Submits the claim and shows exactly what is known afterwards. Only a claim the network
     * confirmed (or already had) is stored as done, and only one that failed on chain as failed.
     * A claim that is still waiting is said to be waiting and is never stored as failed. While
     * an earlier transaction of the account is unresolved the claim gate sends nothing, and the
     * screen says so. The progress mark is cleared on every path that keeps the screen open.
     */
    fun nextButtonClicked() {
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
                }
                notSent != null -> {
                    setLoading(false)
                    showTransactionNotSent(resourceManager, notSent)
                }
                outcome == null || outcome is TransactionOutcome.Confirmed ->
                    saveStatus(MigrationStatus.SUCCESS)
                outcome is TransactionOutcome.Waiting -> {
                    setLoading(false)
                    showClaimWaiting()
                }
                outcome is TransactionOutcome.FailedOnChain -> {
                    setLoading(false)
                    saveStatus(MigrationStatus.FAILED)
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

    private fun showClaimWaiting() {
        alertDialogLiveData.setValue(
            resourceManager.getString(R.string.tx_outcome_waiting_title) to
                resourceManager.getString(R.string.tx_outcome_claim_waiting)
        )
    }

    /**
     * The claim gate refuses to sign a claim while any earlier transaction of this account has
     * no final status yet (a waiting claim, another waiting transaction, or one the node refused
     * whose validity window has not been scanned to its end).
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
    }
}
