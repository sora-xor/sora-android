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

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.domain.RepeatStrategyBuilder
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_main_impl.domain.subs.GlobalSubscriptionManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsUpdateSubscription
import jp.co.soramitsu.feature_select_node_api.NodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The waits before each new try of a first balance read that failed. */
internal val BALANCE_RETRY_WAITS_MILLIS = listOf(2_000L, 5_000L, 15_000L, 45_000L)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val assetsInteractor: AssetsInteractor,
    nodeManager: NodeManager,
    private val pinCodeInteractor: PinCodeInteractor,
    private val globalSubscriptionManager: GlobalSubscriptionManager,
    private val blockExplorerManager: BlockExplorerManager,
    private val coroutineManager: CoroutineManager,
    private val poolsUpdateSubscription: PoolsUpdateSubscription,
) : BaseViewModel() {

    private val _badConnectionVisibilityLiveData = MutableLiveData<Boolean>()
    val badConnectionVisibilityLiveData: LiveData<Boolean> = _badConnectionVisibilityLiveData

    private val _isPincodeUpdateNeeded = MutableLiveData<Boolean>()
    val isPincodeUpdateNeeded: LiveData<Boolean> = _isPincodeUpdateNeeded

    init {
        viewModelScope.launch {
            globalSubscriptionManager
                .start()
                // Background work: its failures show in the connection banner, not in a dialog.
                .catch { onBackgroundError(it) }
                .flowOn(coroutineManager.io)
                .collect()
        }
        viewModelScope.launch {
            tryCatch {
                nodeManager.connectionState
                    .collectLatest {
                        _badConnectionVisibilityLiveData.setValueIfNew(!it)
                    }
            }
        }
        viewModelScope.launch(coroutineManager.io) {
            assetsInteractor.flowCurSoraAccount()
                .catch { onBackgroundError(it) }
                .collectLatest { account ->
                    try {
                        // The balances are read at once, with the token list as stored. The
                        // pools and the liquidity need the list as this launch's walk leaves it
                        // (every asset on the chain, the whitelist), as they always had it: the
                        // second read waits for the walk, and reads the balances once more when
                        // the walk changed the list. Both read the wallet this collector was
                        // started for; once it is no longer the selected one, nothing more is
                        // done here (the next account's collector does it, after its own walk).
                        val address = account.substrateAddress
                        if (!readBalances { assetsInteractor.updateWhitelistBalances(address) }) return@collectLatest
                        if (!readBalances { assetsInteractor.updateWhitelistBalancesAfterTokenListWalk(address) }) {
                            return@collectLatest
                        }
                        poolsUpdateSubscription.updateBasicPools()
                        assetsInteractor.getTokensList().map { it.id }.also { tokens ->
                            blockExplorerManager.getTokensLiquidity(tokens)
                        }
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        jp.co.soramitsu.common.logger.WalletDiagnostics.recordException(error)
                        _badConnectionVisibilityLiveData.postValue(true)
                    }
                }
        }
        viewModelScope.launch {
            withContext(coroutineManager.io) {
                RepeatStrategyBuilder.infinite().repeat {
                    // A periodic refresh: a failed round is recorded and tried again; it never
                    // opens a dialog.
                    try {
                        blockExplorerManager.updateFiat()
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        onBackgroundError(error)
                    }
                    delay(20000)
                }
            }
        }
    }

    /**
     * Until the wallet's balances have been read once, the home shows what an earlier launch
     * stored. A first read that fails is therefore tried again a few times, a little later each
     * time; until 2026-11-22 only the next balance change or account change made up for it. The
     * connection banner shows from the first failure. When every try fails the last error is
     * thrown; switching accounts ends the tries for the account that was left.
     */
    private suspend fun <T> readBalances(read: suspend () -> T): T {
        var failure = try {
            return read()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }
        _badConnectionVisibilityLiveData.postValue(true)
        for (wait in BALANCE_RETRY_WAITS_MILLIS) {
            jp.co.soramitsu.common.logger.WalletDiagnostics.recordException(failure)
            delay(wait)
            try {
                // The chain answered: the banner this read raised goes again.
                return read().also { _badConnectionVisibilityLiveData.postValue(false) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
        }
        throw failure
    }

    fun showPinFragment() {
        viewModelScope.launch {
            // Reading the PIN decrypts it. If the wallet key is busy or missing that fails; the
            // PIN screen is still shown (it checks again and never replaces the stored PIN) and
            // the app does not crash.
            _isPincodeUpdateNeeded.value = try {
                pinCodeInteractor.isPincodeUpdateNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                jp.co.soramitsu.common.logger.WalletDiagnostics.recordException(error)
                false
            }
        }
    }
}
