/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.common.R

object Dimens {

    val x05: Dp
        @Composable get() = dimensionResource(id = R.dimen.x1_2)

    val x1: Dp
        @Composable get() = dimensionResource(id = R.dimen.x1)

    val x2: Dp
        @Composable get() = dimensionResource(id = R.dimen.x2)

    val x3: Dp
        @Composable get() = dimensionResource(id = R.dimen.x3)

    val x4: Dp
        @Composable get() = dimensionResource(id = R.dimen.x4)

    val x5: Dp
        @Composable get() = dimensionResource(id = R.dimen.x5)

    val x6: Dp
        @Composable get() = dimensionResource(id = R.dimen.x6)

    val x7: Dp
        @Composable get() = dimensionResource(id = R.dimen.x7)

    val x9: Dp
        @Composable get() = dimensionResource(id = R.dimen.x9)
}
