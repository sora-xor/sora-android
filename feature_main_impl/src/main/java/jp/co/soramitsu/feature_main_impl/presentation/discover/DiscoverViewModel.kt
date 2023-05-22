/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.discover

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
) : BaseViewModel() {

    fun onAddLiquidityClick() {
        polkaswapRouter.showAddLiquidity(SubstrateOptionsProvider.feeAssetId)
    }
}
