/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
