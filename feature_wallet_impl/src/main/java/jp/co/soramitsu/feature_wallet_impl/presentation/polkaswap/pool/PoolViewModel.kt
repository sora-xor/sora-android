/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PoolsManager
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool.model.PoolModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ExperimentalCoroutinesApi
@FlowPreview
@HiltViewModel
class PoolViewModel @Inject constructor(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val poolsManager: PoolsManager,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

    sealed class PoolsState {
        object Error : PoolsState()
        object Empty : PoolsState()
        class PoolsList(val pools: List<PoolModel>, val loading: Boolean) : PoolsState()
    }

    private val _poolModelLiveData =
        MutableLiveData<PoolsState>(PoolsState.PoolsList(emptyList(), true))
    val poolModelLiveData: LiveData<PoolsState> = _poolModelLiveData

    private var xorAssetToken: Token? = null

    init {
        poolsManager.bind()

        poolsManager.isLoading().combine(polkaswapInteractor.subscribePoolsCache()) { t1, t2 ->
            t1 to t2
        }
            .distinctUntilChanged()
            .debounce(500)
            .map {
                val xorToken = walletInteractor.getFeeToken()
                xorAssetToken = xorToken

                it.first to it.second.map { toAsset ->
                    PoolModel(
                        xorToken,
                        toAsset.token,
                        toAsset.xorPooled,
                        toAsset.secondPooled,
                        toAsset.strategicBonusApy,
                        toAsset.poolShare
                    )
                }
            }
            .catch {
                onError(it)
                _poolModelLiveData.value = PoolsState.Error
            }
            .onEach {
                _poolModelLiveData.value =
                    if (!it.first && it.second.isEmpty()) PoolsState.Empty else PoolsState.PoolsList(
                        it.second,
                        it.first
                    )
            }
            .launchIn(viewModelScope)
    }

    fun onAddLiquidity(tokenFrom: Token, tokenTo: Token? = null) {
        router.showAddLiquidity(tokenFrom, tokenTo)
    }

    fun onAddNewLiquidity() {
        xorAssetToken?.let { token ->
            onAddLiquidity(token)
        }
    }

    fun onRemoveLiquidity(tokenFrom: Token, tokenTo: Token) {
        router.showRemoveLiquidity(tokenFrom, tokenTo)
    }

    override fun onCleared() {
        poolsManager.unbind()
        super.onCleared()
    }
}
