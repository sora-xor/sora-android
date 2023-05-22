/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

data class SoraCardInformation(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTime: Long,
    val kycStatus: String
)
