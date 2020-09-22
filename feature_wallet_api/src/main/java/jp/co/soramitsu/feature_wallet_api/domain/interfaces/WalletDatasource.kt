package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta

interface WalletDatasource {

    fun saveBalance(balance: Array<Asset>)

    fun retrieveBalance(): Array<Asset>?

    fun saveContacts(results: List<Account>)

    fun retrieveContacts(): List<Account>?

    fun saveTransferMeta(transferMeta: TransferMeta)

    fun observeTransferMeta(): Observable<TransferMeta>

    fun saveWithdrawMeta(transferMeta: TransferMeta)

    fun observeWithdrawMeta(): Observable<TransferMeta>
}