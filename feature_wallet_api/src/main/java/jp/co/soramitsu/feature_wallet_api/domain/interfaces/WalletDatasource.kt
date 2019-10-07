/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta

interface WalletDatasource {

    fun saveBalance(balance: Array<Asset>)

    fun retrieveBalance(): Array<Asset>?

    fun saveContacts(results: List<Account>)

    fun retrieveContacts(): List<Account>?

    fun saveTransferMeta(transferMeta: TransferMeta)

    fun retrieveTransferMeta(): TransferMeta?
}