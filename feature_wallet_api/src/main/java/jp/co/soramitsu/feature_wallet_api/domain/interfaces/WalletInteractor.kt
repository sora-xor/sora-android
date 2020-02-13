package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferMeta
import jp.co.soramitsu.feature_wallet_api.domain.model.WithdrawalMeta
import java.math.BigDecimal

interface WalletInteractor {

    fun getAccountId(): Single<String>

    fun getBalance(updateCached: Boolean): Single<BigDecimal>

    fun getTransactionHistory(updateCached: Boolean, adding: Boolean): Single<List<Transaction>>

    fun findOtherUsersAccounts(search: String): Single<List<Account>>

    fun getQrCodeAmountString(amount: String): Single<String>

    fun transferAmount(amount: String, accountId: String, description: String, fee: String): Single<String>

    fun getContacts(updateCached: Boolean): Single<List<Account>>

    fun withdrawFlow(amount: String, ethAddress: String, notaryAddress: String, feeAddress: String, feeAmount: String): Completable

    fun getBalanceAndWithdrawalMeta(): Single<Pair<BigDecimal, WithdrawalMeta>>

    fun getBalanceAndTransferMeta(updateCached: Boolean): Single<Pair<BigDecimal, TransferMeta>>

    fun processQr(contents: String): Single<Pair<BigDecimal, Account>>
}