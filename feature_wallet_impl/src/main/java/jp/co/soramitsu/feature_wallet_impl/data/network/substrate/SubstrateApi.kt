package jp.co.soramitsu.feature_wallet_impl.data.network.substrate

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.AssetInfoDto
import jp.co.soramitsu.common.data.network.dto.EventRecord
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.model.BlockResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import java.math.BigInteger

interface SubstrateApi {
    fun fetchBalance(accountId: String, assetId: String): Single<BigInteger>
    fun fetchAssetList(runtime: RuntimeSnapshot): Single<List<AssetInfoDto>>
    fun needsMigration(irohaAddress: String): Single<Boolean>
    fun transfer(keypair: Keypair, from: String, to: String, assetId: String, amount: BigInteger, runtime: RuntimeSnapshot): Single<String>
    fun observeTransfer(keypair: Keypair, from: String, to: String, assetId: String, amount: BigInteger, runtime: RuntimeSnapshot): Observable<Pair<String, ExtrinsicStatusResponse>>
    fun calcFee(from: String, to: String, assetId: String, amount: BigInteger, runtime: RuntimeSnapshot): Single<BigInteger>
    fun checkEvents(runtime: RuntimeSnapshot, blockHash: String): Single<List<EventRecord>>
    fun getBlock(blockHash: String): Single<BlockResponse>
    fun migrate(irohaAddress: String, irohaPublicKey: String, signature: String, keypair: Keypair, runtime: RuntimeSnapshot, address: String): Observable<Pair<String, ExtrinsicStatusResponse>>
    fun unwatchExtrinsic(subscription: String): Completable
    fun fetchBalances(runtime: RuntimeSnapshot, accountId: String): Single<BigInteger>
    fun isUpgradedToDualRefCount(runtime: RuntimeSnapshot): Single<Boolean>
}
