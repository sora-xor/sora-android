/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_impl.data.network.model.AccountRemote

fun mapAccountRemoteToAccount(accountRemote: AccountRemote): Account {
    return with(accountRemote) {
        Account(firstName, lastName, accountId)
    }
}