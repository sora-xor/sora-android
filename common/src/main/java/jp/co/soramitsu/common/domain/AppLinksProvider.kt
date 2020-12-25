/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

data class AppLinksProvider(
    val soraHostUrl: String,
    val defaultMarketUrl: String,
    val inviteUrl: String,
    val blockChainExplorerUrl: String
)