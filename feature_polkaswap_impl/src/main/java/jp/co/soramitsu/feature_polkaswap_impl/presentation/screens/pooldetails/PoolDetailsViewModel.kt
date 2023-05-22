/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.pooldetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_polkaswap_impl.presentation.states.PoolDetailsState
import jp.co.soramitsu.ui_core.component.toolbar.Action
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PoolDetailsViewModel @AssistedInject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val numbersFormatter: NumbersFormatter,
    private val polkaswapRouter: PolkaswapRouter,
    @Assisted("id1") private val token1Id: String,
    @Assisted("id2") private val token2Id: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedPoolDetailsViewModelFactory {
        fun create(@Assisted("id1") id1: String, @Assisted("id2") id2: String): PoolDetailsViewModel
    }

    internal var detailsState by mutableStateOf(
        PoolDetailsState(
            DEFAULT_ICON_URI, DEFAULT_ICON_URI,
            "", "", "", "", "",
            true, true,
        )
    )

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.pool_details,
                navIcon = null,
                menu = listOf(Action.Close()),
            ),
        )
        viewModelScope.launch {
            poolsInteractor.subscribePoolCache(token1Id, token2Id)
                .catch { onError(it) }
                .collectLatest { data ->
                    if (data == null) {
                        detailsState = detailsState.copy(
                            addEnabled = false,
                            removeEnabled = false,
                        )
                    } else {
                        detailsState = PoolDetailsState(
                            token1Icon = data.baseToken.iconUri(),
                            token2Icon = data.token.iconUri(),
                            symbol1 = data.baseToken.symbol,
                            symbol2 = data.token.symbol,
                            apy = data.strategicBonusApy?.let { apy ->
                                "%s%%".format(
                                    numbersFormatter.format(
                                        apy,
                                        2,
                                    )
                                )
                            } ?: "",
                            pooled1 = data.baseToken.printBalance(
                                data.basePooled,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            ),
                            pooled2 = data.token.printBalance(
                                data.secondPooled,
                                numbersFormatter,
                                AssetHolder.ROUNDING
                            ),
                            addEnabled = true,
                            removeEnabled = true,
                        )
                    }
                }
        }
    }

    override fun onMenuItem(action: Action) {
        this.onBackPressed()
    }

    fun onSupply() {
        polkaswapRouter.showAddLiquidity(token1Id, token2Id)
    }

    fun onRemove() {
        polkaswapRouter.showRemoveLiquidity(token1Id to token2Id)
    }
}
