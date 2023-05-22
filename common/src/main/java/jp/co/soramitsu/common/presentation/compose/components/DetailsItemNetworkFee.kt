/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.R

@Composable
fun DetailsItemNetworkFee(
    modifier: Modifier = Modifier,
    fee: String,
    feeFiat: String? = null,
) {
    DetailsItem(
        modifier = modifier,
        text = stringResource(id = R.string.network_fee),
        hint = stringResource(id = R.string.polkaswap_network_fee_info),
        value1 = fee,
        value2 = feeFiat,
    )
}
