/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.dto

data class TokenInfoDto(
    val id: String,
    val name: String,
    val symbol: String,
    val precision: Int,
    val isMintable: Boolean,
)
