/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.sora.substrate.models.ExtrinsicStatusResponse
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.Events
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile

@Singleton
class ExtrinsicManager @Inject constructor(
    private val calls: SubstrateCalls,
    private val factory: ExtrinsicBuilderFactory,
    private val coroutineManager: CoroutineManager,
    private val runtimeManager: RuntimeManager,
) {

    fun interface WatchingListener {
        fun onChange(txHash: String, success: Boolean, block: String?)
    }

    private var watchingListener: WatchingListener? = null

    suspend fun calcFee(
        from: String,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): BigInteger? {
        val builder = factory.create(from)
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        return calls.getExtrinsicFee(extrinsic)
    }

    suspend fun submitExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): Result<String> = runCatching {
        val builder = factory.create(from, keypair)
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        calls.submitExtrinsic(extrinsic)
    }

    suspend fun submitAndWaitExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        untilStatus: String = SubstrateCalls.FINALIZED,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        val builder = factory.create(from, keypair)
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        val result = calls.submitAndWatchExtrinsic(
            extrinsic,
            untilStatus,
        )
            .catch {
                FirebaseWrapper.recordException(it)
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .transformWhile { value ->
                val txHash = value.first
                val finish = getFinish(value.second, txHash)
                finish.first?.let {
                    watchingListener?.onChange(txHash, it, finish.second)
                }
                emit(
                    if (txHash.isEmpty()) ExtrinsicSubmitStatus(
                        success = false,
                        txHash = txHash,
                        blockHash = finish.second,
                    ) else finish.first?.let {
                        ExtrinsicSubmitStatus(
                            success = it,
                            txHash = txHash,
                            blockHash = finish.second,
                        )
                    }
                )
                finish.first == null && txHash.isNotEmpty()
            }.filterNotNull().first()
        return result
    }

    suspend fun submitAndWatchExtrinsic(
        from: String,
        keypair: Sr25519Keypair,
        useBatchAll: Boolean = false,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): ExtrinsicSubmitStatus {
        val builder = factory.create(from, keypair)
        builder.formExtrinsic()
        val extrinsic = builder.build(useBatchAll)
        val result = calls.submitAndWatchExtrinsic(
            extrinsic,
            SubstrateCalls.FINALIZED,
        )
            .catch {
                FirebaseWrapper.recordException(it)
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .transformWhile { value ->
                val txHash = value.first
                val finish = getFinish(value.second, txHash)
                finish.first?.let {
                    watchingListener?.onChange(txHash, it, finish.second)
                }
                emit(
                    ExtrinsicSubmitStatus(
                        success = txHash.isNotEmpty(),
                        txHash = txHash,
                        blockHash = finish.second,
                    )
                )
                finish.first == null && txHash.isNotEmpty()
            }.stateIn(coroutineManager.applicationScope).first()
        return result
    }

    fun setWatchingExtrinsicListener(listener: WatchingListener) {
        watchingListener = listener
    }

    private suspend fun getFinish(
        response: ExtrinsicStatusResponse,
        txHash: String
    ): Pair<Boolean?, String?> =
        when (response) {
            is ExtrinsicStatusResponse.ExtrinsicStatusFinalityTimeout -> false to null
            is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> {
                val blockHash = response.inBlock
                val blockResponse = calls.getBlock(blockHash)
                val extrinsicId =
                    blockResponse.block.extrinsics.indexOfFirst { s -> s.extrinsicHash() == txHash }
                        .toLong()
                val isSuccess = isTxSuccessful(extrinsicId, blockHash, txHash)
                isSuccess to blockHash
            }
            is ExtrinsicStatusResponse.ExtrinsicStatusPending -> null to null
        }

    private suspend fun isTxSuccessful(
        extrinsicId: Long,
        blockHash: String,
        txHash: String
    ): Boolean {
        val blockEvents = calls.checkEvents(blockHash)
        if (blockEvents.isEmpty()) {
            FirebaseWrapper.recordException(Throwable("Events list is empty in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
            return false
        }
        val (moduleIndexSuccess, eventIndexSuccess) = runtimeManager.getRuntimeSnapshot().metadata.module(
            Pallete.SYSTEM.palletName
        ).event(Events.EXTRINSIC_SUCCESS.eventName).index
        val (moduleIndexFailed, eventIndexFailed) = runtimeManager.getRuntimeSnapshot().metadata.module(
            Pallete.SYSTEM.palletName
        ).event(Events.EXTRINSIC_FAILED.eventName).index
        val successEvent = blockEvents.find { event ->
            (event.module == moduleIndexSuccess && event.event == eventIndexSuccess && event.number == extrinsicId) ||
                (event.module == Pallete.SYSTEM.palletName && event.event == Events.EXTRINSIC_SUCCESS.eventName && event.number == extrinsicId)
        }
        val failedEvent = blockEvents.find { event ->
            (event.module == moduleIndexFailed && event.event == eventIndexFailed && event.number == extrinsicId) ||
                (event.module == Pallete.SYSTEM.palletName && event.event == Events.EXTRINSIC_FAILED.eventName && event.number == extrinsicId)
        }
        return when {
            successEvent != null -> {
                true
            }
            failedEvent != null -> {
                false
            }
            else -> {
                FirebaseWrapper.recordException(Throwable("No success or failed event in blockhash $blockHash via extrinsicId $extrinsicId and txHash $txHash"))
                false
            }
        }
    }
}
