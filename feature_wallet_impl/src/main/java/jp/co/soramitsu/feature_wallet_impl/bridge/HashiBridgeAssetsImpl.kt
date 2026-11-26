package jp.co.soramitsu.feature_wallet_impl.bridge

import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.HashiBridgeAssets
import kotlinx.coroutines.CancellationException

class HashiBridgeAssetsImpl(
    private val service: HashiBridgeService,
    override val supported: Boolean = BuildUtils.isFlavors(Flavor.PROD),
) : HashiBridgeAssets {

    override suspend fun isRegistered(assetId: String): Boolean {
        if (!supported) return false
        return try {
            service.isRegistered(assetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }
}
