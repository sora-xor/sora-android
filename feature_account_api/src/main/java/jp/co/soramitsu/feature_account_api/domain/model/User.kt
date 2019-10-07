/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_api.domain.model

data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val status: String,
    val parentId: String,
    val country: String
)