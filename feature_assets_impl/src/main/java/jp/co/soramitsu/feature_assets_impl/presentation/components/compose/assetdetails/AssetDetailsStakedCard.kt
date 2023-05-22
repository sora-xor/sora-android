/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R

@Composable
internal fun AssetDetailsStakedCard() {
    AssetDetailsCard(
        title = stringResource(id = R.string.asset_details_token_price),
        amount = "",
    ) {
    }
}
