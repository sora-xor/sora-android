/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.polkaswap.pool

import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

@ExperimentalCoroutinesApi
@FlowPreview
class PoolViewModel(
    private val router: WalletRouter,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager,
) : BaseViewModel() {

//    companion object {
//        private const val POOL_SHARE_PRECISION = 8
//    }
//
//    private val _poolModelLiveData = MutableLiveData<List<PoolModel>>()
//    val poolModelLiveData: LiveData<List<PoolModel>> = _poolModelLiveData
//
//    init {
//        viewModelScope.launch {
//            polkaswapInteractor.updatePools()
//        }
//
//        polkaswapInteractor.subscribePoolsCache()
//            .distinctUntilChanged()
//            .debounce(500)
//            .map {
//                val xorAsset = walletInteractor.getAsset(OptionsProvider.feeAssetId)!!
//
//                it.map {
//                    PoolModel(
//                        xorAsset.token.symbol,
//                        xorAsset.token.icon,
//                        it.token.symbol,
//                        it.token.icon,
//                        numbersFormatter.formatBigDecimal(it.xorPooled, POOL_SHARE_PRECISION),
//                        numbersFormatter.formatBigDecimal(it.secondPooled, POOL_SHARE_PRECISION),
//                        numbersFormatter.format(it.poolShare)
//                    )
//                }
//            }
//            .catch {
//                onError(it)
//            }
//            .onEach {
//                _poolModelLiveData.value = it
//            }
//            .launchIn(viewModelScope)
//
//        polkaswapInteractor.subscribePoolsChanges()
//            .distinctUntilChanged()
//            .debounce(500)
//            .catch {
//                onError(it)
//            }
//            .onEach {
//                polkaswapInteractor.updatePools()
//            }
//            .launchIn(viewModelScope)
//    }
}
