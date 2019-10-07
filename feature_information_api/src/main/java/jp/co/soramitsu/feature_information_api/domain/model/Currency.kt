/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_api.domain.model

data class Currency(
    val code: String,
    val symbol: String,
    val name: String,
    val ratio: Float,
    var isSelected: Boolean
)