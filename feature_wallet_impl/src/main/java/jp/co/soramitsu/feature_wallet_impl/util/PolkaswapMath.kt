/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.util

import java.math.BigDecimal

object PolkaswapMath {

    fun BigDecimal.isZero(): Boolean = this.compareTo(BigDecimal.ZERO) == 0
}
