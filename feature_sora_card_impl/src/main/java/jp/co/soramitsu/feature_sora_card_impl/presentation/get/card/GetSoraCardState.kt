/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.SoraCardInformation

data class GetSoraCardState(
    val xorBalance: BigDecimal = BigDecimal.ZERO,
    val enoughXor: Boolean = false,
    val percent: BigDecimal = BigDecimal.ZERO,
    val needInXor: String = "",
    val needInEur: String = "",
    val getMorXorAlert: Boolean = false,
    val xorRatioAvailable: Boolean = false,
    val soraCardInfo: SoraCardInformation? = null,
    val connection: Boolean = false,
)
