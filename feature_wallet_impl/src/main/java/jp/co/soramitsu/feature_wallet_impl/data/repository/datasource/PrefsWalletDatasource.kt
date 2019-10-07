/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.FeeType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import javax.inject.Inject

class PrefsWalletDatasource @Inject constructor(
    private val prefsUtl: PrefsUtil
) : WalletDatasource {

    companion object {
        private const val KEY_BALANCE = "key_balance"
        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_TRANSFER_META_FEE_RATE = "key_transfer_meta_rate"
        private const val KEY_TRANSFER_META_FEE_TYPE = "key_transfer_meta_type"
    }

    private val gson = Gson()

    override fun saveBalance(balance: Array<Asset>) {
        prefsUtl.putString(KEY_BALANCE, gson.toJson(balance))
    }

    override fun retrieveBalance(): Array<Asset>? {
        val balanceJson = prefsUtl.getString(KEY_BALANCE)

        return if (balanceJson.isEmpty()) {
            null
        } else {
            gson.fromJson<Array<Asset>>(balanceJson, object : TypeToken<Array<Asset>>() {}.type)
        }
    }

    override fun saveContacts(results: List<Account>) {
        prefsUtl.putString(KEY_CONTACTS, gson.toJson(results))
    }

    override fun retrieveContacts(): List<Account>? {
        val contactsJson = prefsUtl.getString(KEY_CONTACTS)

        return if (contactsJson.isEmpty()) {
            null
        } else {
            gson.fromJson<List<Account>>(contactsJson, object : TypeToken<List<Account>>() {}.type)
        }
    }

    override fun saveTransferMeta(transferMeta: TransferMeta) {
        prefsUtl.putDouble(KEY_TRANSFER_META_FEE_RATE, transferMeta.feeRate)
        prefsUtl.putString(KEY_TRANSFER_META_FEE_TYPE, transferMeta.feeType.toString())
    }

    override fun retrieveTransferMeta(): TransferMeta? {
        val feeRate = prefsUtl.getDouble(KEY_TRANSFER_META_FEE_RATE, -1.0)

        if (feeRate != -1.0) {
            return TransferMeta(feeRate, FeeType.valueOf(prefsUtl.getString(KEY_TRANSFER_META_FEE_TYPE)))
        }

        return null
    }
}