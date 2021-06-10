/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.data.network.substrate.SubstrateNetworkOptionsProvider
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.exceptions.QrException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.blake2b256String
import org.bouncycastle.util.encoders.Hex
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val ethRepository: EthereumRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cryptoAssistant: CryptoAssistant,
) : WalletInteractor {

    override fun saveMigrationStatus(migrationStatus: MigrationStatus): Completable {
        return walletRepository.saveMigrationStatus(migrationStatus)
    }

    override fun observeMigrationStatus(): Observable<MigrationStatus> {
        return walletRepository.observeMigrationStatus()
    }

    override fun needsMigration(): Single<Boolean> {
        return credentialsRepository.getIrohaAddress()
            .flatMap { walletRepository.needsMigration(it) }
            .map { userRepository.saveNeedsMigration(it).blockingAwait(); it }
    }

    override fun migrate(): Single<Boolean> {
        return credentialsRepository.getClaimSignature()
            .flatMap { signature ->
                credentialsRepository.getIrohaAddress().map { Pair(it, signature) }
            }
            .flatMap { pair ->
                credentialsRepository.retrieveIrohaKeyPair()
                    .map { Triple(pair.first, Hex.toHexString(it.public.encoded), pair.second) }
            }
            .flatMap { triple ->
                credentialsRepository.retrieveKeyPair()
                    .flatMap { keypair ->
                        credentialsRepository.getAddress().flatMap { address ->
                            walletRepository.migrate(triple.first, triple.second, triple.third, keypair, address)
                                .map {
                                    Triple(
                                        it.first,
                                        (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                                        it.second.subscription
                                    )
                                }.lastOrError()
                        }
                    }.flatMap {
                        val txHash = it.first
                        val blockHash = requireNotNull(it.second)
                        val subscription = it.third

                        walletRepository.getBlock(blockHash)
                            .map { blockResponse ->
                                blockResponse.block.extrinsics.indexOfFirst { s ->
                                    s.blake2b256String() == txHash
                                }.toLong()
                            }.flatMap { extrinsicId ->
                                walletRepository.isTxSuccessful(extrinsicId, blockHash, txHash)
                            }
                            .map {
                                it to subscription
                            }
                    }.flatMap {
                        userRepository.saveNeedsMigration(!it.first).toSingleDefault(it)
                    }.flatMap {
                        walletRepository.unwatch(it.second).toSingleDefault(it.first)
                    }
            }
    }

    override fun calcTransactionFee(
        to: String,
        assetId: String,
        amount: BigDecimal
    ): Single<BigDecimal> {
        return credentialsRepository.getAddress().flatMap {
            walletRepository.calcTransactionFee(it, to, assetId, amount)
        }
    }

    override fun transfer(to: String, assetId: String, amount: BigDecimal): Single<String> =
        credentialsRepository.getAddress().flatMap { address ->
            credentialsRepository.retrieveKeyPair().flatMap { keypair ->
                walletRepository.transfer(keypair, address, to, assetId, amount)
            }
        }

    override fun observeTransfer(
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal
    ): Completable {
        return credentialsRepository.getAddress()
            .flatMap { address ->
                credentialsRepository.retrieveKeyPair().map { keypair ->
                    address to keypair
                }
            }
            .flatMapCompletable {
                observeTransferInternal(it.second, it.first, to, assetId, amount, fee)
            }
    }

    private fun observeTransferInternal(
        keypair: Keypair,
        addressFrom: String,
        to: String,
        assetId: String,
        amount: BigDecimal,
        fee: BigDecimal,
    ): Completable {
        return Completable.create { emitter ->
            walletRepository.observeTransfer(
                keypair,
                addressFrom,
                to,
                assetId,
                amount,
                fee
            )
                .map {
                    walletRepository.saveTransaction(
                        addressFrom,
                        to,
                        assetId,
                        amount,
                        it.second,
                        it.first,
                        fee,
                        null
                    )
                    if (!emitter.isDisposed) emitter.onComplete()
                    Triple(
                        it.first,
                        (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                        it.second.subscription
                    )
                }.lastOrError()
                .flatMap {
                    val txHash = it.first
                    val blockHash = requireNotNull(it.second, { "Block hash is null" })
                    val subscription = it.third

                    walletRepository.getBlock(blockHash)
                        .map { blockResponse ->
                            blockResponse.block.extrinsics.indexOfFirst { s ->
                                s.blake2b256String() == txHash
                            }.toLong()
                        }.flatMap { extrinsicId ->
                            walletRepository.isTxSuccessful(extrinsicId, blockHash, txHash)
                        }.map {
                            Triple(txHash, subscription, it)
                        }
                }
                .map {
                    walletRepository.updateTransactionSuccess(it.first, it.third); it.second
                }.flatMapCompletable {
                    walletRepository.unwatch(it)
                }
                .subscribeOn(Schedulers.io())
                .subscribe(
                    {
                        if (!emitter.isDisposed) emitter.onComplete()
                    },
                    {
                        if (!emitter.isDisposed) emitter.onError(it)
                    }
                )
        }
    }

    override fun getAssets(forceUpdateBalance: Boolean, forceUpdateAssets: Boolean): Single<List<Asset>> =
        credentialsRepository.getAddress().flatMap {
            walletRepository.getAssets(it, forceUpdateBalance, forceUpdateAssets)
        }

    override fun getAccountId(): Single<String> {
        return credentialsRepository.getAddress()
    }

    override fun getPublicKey(): Single<ByteArray> {
        return credentialsRepository.getAddressId()
    }

    override fun getPublicKeyHex(withPrefix: Boolean): Single<String> {
        return credentialsRepository.getAddressId().map {
            it.toHexString(withPrefix)
        }
    }

    override fun getAccountName(): Single<String> = userRepository.getAccountName()

    override fun getTransactions(): Observable<List<Transaction>> {
        return credentialsRepository.getAddress()
            .flatMapObservable {
                walletRepository.getTransactions(it, "")
            }.map { list ->
                list.map {
                    it.peerId?.let { address ->
                        it.peerAddressId = address.toAccountId()
                    }
                    it
                }
            }
    }

    override fun findOtherUsersAccounts(search: String): Single<List<Account>> {
        return credentialsRepository.getAddress()
            .flatMap { userAccountId ->
                credentialsRepository.isAddressOk(search).map {
                    if (it && userAccountId != search) {
                        listOf(Account("", "", search))
                    } else {
                        emptyList()
                    }
                }
            }.flatMap { accounts ->
                walletRepository.getContacts(search)
                    .map { set -> set.map { s -> Account("", "", s) } }.map { list ->
                        listOf(list, accounts).flatten()
                    }
            }
    }

    override fun getContacts(query: String): Single<List<Account>> {
        return walletRepository.getContacts(query).map { set ->
            set.map {
                Account("", "", it)
            }
        }
    }

    override fun processQr(contents: String): Single<Triple<String, String, BigDecimal>> =
        credentialsRepository.getAddress().flatMap { myAddress ->
            val list = contents.split(':')
            val result = if (list.size == 5) {
                if (list[0] == SubstrateNetworkOptionsProvider.substrate) {
                    val address = list[1]
                    if (address == myAddress) throw QrException.sendingToMyselfError() else
                        credentialsRepository.isAddressOk(address).map {
                            if (!it) throw QrException.userNotFoundError() else Triple(
                                address,
                                list[4],
                                BigDecimal.ZERO
                            )
                        }
                } else {
                    throw QrException.decodeError()
                }
            } else {
                throw QrException.decodeError()
            }
            result
        }

    override fun hideAssets(assetIds: List<String>): Completable {
        return walletRepository.hideAssets(assetIds)
    }

    override fun displayAssets(assetIds: List<String>): Completable {
        return walletRepository.displayAssets(assetIds)
    }

    override fun updateAssetPositions(assetPositions: Map<String, Int>): Completable {
        return walletRepository.updateAssetPositions(assetPositions)
    }
}
