/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_referral_impl.domain.model

import java.math.BigDecimal

data class Referral(val address: String, val xorAmount: BigDecimal)
