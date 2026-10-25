package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.components.PoolsList
import jp.co.soramitsu.common_wallet.presentation.compose.states.AssetCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BackupWalletState
import jp.co.soramitsu.common_wallet.presentation.compose.states.BasicBannerCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.CardsState
import jp.co.soramitsu.common_wallet.presentation.compose.states.FavoritePoolsCardState
import jp.co.soramitsu.common_wallet.presentation.compose.states.ReferralState
import jp.co.soramitsu.common_wallet.presentation.compose.states.TitledAmountCardState
import jp.co.soramitsu.feature_wallet_impl.data.nexus.NexusPortfolioBalance
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

/**
 * The home's secondary cards under the Portfolio view's list: pools, the other networks' XOR,
 * referral, backup and Edit view. The hero and the Tokens lens are the home's assets.
 */
@Composable
internal fun PortfolioSecondaryCards(
    state: CardsState,
    sora2Portfolio: Sora2PortfolioBalance?,
    nexusPortfolio: List<NexusPortfolioBalance>,
    onPoolClick: (StringPair) -> Unit,
    onOpenFullCardClick: (AssetCardState) -> Unit,
    onReferralClick: () -> Unit,
    onReferralClose: () -> Unit,
    onBackupBannerClick: () -> Unit,
    onEdit: () -> Unit,
    onNexusSend: (NexusPortfolioBalance) -> Unit,
    onNexusRefresh: () -> Unit,
    onNexusLoadMore: (NexusPortfolioBalance) -> Unit,
    isNexusBalanceCurrent: (NexusPortfolioBalance) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val networkSection: @Composable () -> Unit = {
            val walletNetworks = nexusPortfolio.filter { it.walletId == sora2Portfolio?.address }
            if (walletNetworks.isNotEmpty()) {
                OtherNetworksCard(
                    balances = walletNetworks,
                    xorIcon = sora2Portfolio?.xorIcon,
                    xorName = sora2Portfolio?.xorName,
                    isNexusBalanceCurrent = isNexusBalanceCurrent,
                    onSend = onNexusSend,
                    onRecovery = onBackupBannerClick,
                    onRefresh = onNexusRefresh,
                    onLoadMore = onNexusLoadMore,
                    walletId = sora2Portfolio?.address,
                )
                Spacer(modifier = Modifier.size(size = 16.dp))
            }
        }
        val orderedCards = state.cards.sortedBy { if (it is TitledAmountCardState) 0 else 1 }
        val firstBanner = orderedCards.indexOfFirst { it !is TitledAmountCardState }
        orderedCards.forEachIndexed { index, cardState ->
            if (index == firstBanner) networkSection()
            when (cardState) {
                is TitledAmountCardState -> {
                    CommonHubCard(
                        title = cardState.title,
                        amount = cardState.amount,
                        onOpenFullCardClick = { onOpenFullCardClick.invoke(cardState.state) },
                        collapseState = cardState.collapsedState,
                        onCollapseClick = cardState.onCollapseClick,
                    ) {
                        (cardState.state as? FavoritePoolsCardState)?.let { pools ->
                            PoolsList(pools.state, onPoolClick)
                        }
                    }
                }

                is BasicBannerCardState -> {
                    when (cardState) {
                        BackupWalletState -> BackupCard(onStartClicked = onBackupBannerClick)
                        ReferralState -> ReferralCard(
                            onStartClicked = onReferralClick,
                            onCloseCard = onReferralClose,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(size = 16.dp))
        }

        if (firstBanner == -1) networkSection()

        // Keep editing reachable when every optional card is hidden or has no content.
        BleachedButton(
            modifier = Modifier
                .padding(bottom = Dimens.x4)
                .align(Alignment.CenterHorizontally),
            size = Size.Small,
            order = Order.SECONDARY,
            text = stringResource(id = R.string.edit_view),
            onClick = onEdit,
        )
    }
}
