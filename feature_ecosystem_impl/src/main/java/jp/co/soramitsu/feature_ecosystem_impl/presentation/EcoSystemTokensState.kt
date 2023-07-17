package jp.co.soramitsu.feature_ecosystem_impl.presentation

import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetItemCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.assetItemCardStateEmpty

internal data class EcoSystemTokensState(
    val topTokens: List<Pair<String, AssetItemCardState>>,
    val sear: String,
)

internal val initialEcoSystemTokensState = EcoSystemTokensState(
    topTokens = List(5) { i -> (i + 1).toString() to assetItemCardStateEmpty },
    sear = "",
)
