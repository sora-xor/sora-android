/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.network

import io.reactivex.Single
import iroha.protocol.Queries
import jp.co.soramitsu.common.util.ext.toHash
import jp.co.soramitsu.feature_wallet_impl.data.network.request.IrohaRequest
import jp.co.soramitsu.iroha.java.Query
import org.spongycastle.util.encoders.Base64
import java.security.KeyPair
import java.util.Date

class TransactionFactory {

    companion object {
        private const val ASSET_ID = "xor#sora"
    }

    fun buildTransferWithFeeTransaction(
        amount: String,
        myAccountId: String,
        dstUserId: String,
        description: String,
        fee: String,
        keyPair: KeyPair
    ): Single<Pair<IrohaRequest, String>> {
        return Single.fromCallable {
            val txBuilder = jp.co.soramitsu.iroha.java.Transaction.builder(myAccountId)
                .transferAsset(myAccountId, dstUserId, ASSET_ID, description, amount)

            if (fee.isNotEmpty() && fee.toDouble() != 0.0) {
                txBuilder.subtractAssetQuantity(ASSET_ID, fee)
            }

            val tx = txBuilder.setQuorum(2)
                .sign(keyPair)
                .build()

            Pair(IrohaRequest(Base64.toBase64String(tx.toByteArray())), tx.toHash())
        }
    }

    fun buildGetAccountAssetsQuery(accountId: String, keyPair: KeyPair): Single<Queries.Query> {
        return Single.fromCallable {
            Query.builder(accountId, Date(), 1)
                .getAccountAssets(accountId)
                .buildSigned(keyPair)
        }
    }
}