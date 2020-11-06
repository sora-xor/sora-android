/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.data.network

import io.reactivex.Single
import jp.co.soramitsu.common.util.Const.Companion.NOTARY_ADDRESS
import jp.co.soramitsu.common.util.ext.toHash
import jp.co.soramitsu.feature_ethereum_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.iroha.java.Utils
import org.spongycastle.util.encoders.Base64
import java.math.BigDecimal
import java.security.KeyPair

class TransactionFactory {

    companion object {
        private const val ETH_REGISTRATION_ID = "eth_registration_service@notary"
        private const val ETH_REGISTRATION_KEY = "register_wallet"
        private const val ASSET_ID = "val#sora"
    }

    fun buildWithdrawTransaction(
        amount: BigDecimal,
        srcAccountId: String,
        ethAddress: String,
        fee: String,
        keyPair: KeyPair
    ): Single<Pair<IrohaRequest, String>> {
        return Single.fromCallable {
            val txBuilder = jp.co.soramitsu.iroha.java.Transaction.builder(srcAccountId)
                .transferAsset(srcAccountId, NOTARY_ADDRESS, ASSET_ID, ethAddress, amount)

            if (fee.isNotEmpty() && fee.toDouble() != 0.0) {
                txBuilder.subtractAssetQuantity(ASSET_ID, fee)
            }

            val tx = txBuilder.setQuorum(2)
                .sign(keyPair)
                .build()

            val request = IrohaRequest(Base64.toBase64String(tx.toByteArray()))

            Pair(request, tx.toHash())
        }
    }

    fun buildRegisterEthRequest(
        accountId: String,
        serializedValue: String,
        keyPair: KeyPair
    ): Single<Pair<IrohaRequest, String>> {
        return Single.fromCallable {
            val escapedSerializedValue = Utils.irohaEscape(serializedValue)
            val tx = jp.co.soramitsu.iroha.java.Transaction.builder(accountId)
                .setAccountDetail(ETH_REGISTRATION_ID, ETH_REGISTRATION_KEY, escapedSerializedValue)
                .setQuorum(2)
                .sign(keyPair)
                .build()

            val request = IrohaRequest(Base64.toBase64String(tx.toByteArray()))

            Pair(request, tx.toHash())
        }
    }
}