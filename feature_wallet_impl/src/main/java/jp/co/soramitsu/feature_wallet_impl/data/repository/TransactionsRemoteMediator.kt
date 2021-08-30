package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SwapRemoteMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapRemoteErrorTransfersToLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapRemoteTransfersToLocal
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.ceil

@ExperimentalPagingApi
class TransactionsRemoteMediator(
    private val soraScanApi: SoraScanApi,
    private val db: AppDatabase,
    private val myAddress: String,
    private val tokensDeferred: CompletableDeferred<List<Token>>,
) : RemoteMediator<Int, ExtrinsicLocal>() {

    private val transactionDao = db.transactionDao()
    private var endReachedTransferOk = false
    private var endReachedTransferError = false
    private var endReachedSwap = false

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ExtrinsicLocal>
    ): MediatorResult {
        val tokens = tokensDeferred.await()
        return try {
            if ((loadType == LoadType.PREPEND) || (loadType == LoadType.APPEND && state.lastItemOrNull() == null))
                return MediatorResult.Success(endOfPaginationReached = true)

            val countRemote =
                if (loadType == LoadType.REFRESH) state.config.initialLoadSize else state.config.pageSize

            if (loadType == LoadType.REFRESH) {
                endReachedTransferError = false
                endReachedTransferOk = false
                endReachedSwap = false
            }

            // successful transfers
            var pageNumberSuccessful =
                ceil(
                    transactionDao.countTransferNotLocalSuccess().toFloat() / state.config.pageSize
                ).toLong() + 1
            if (loadType == LoadType.REFRESH) pageNumberSuccessful = 1
            val responseTransfersOk = if (!endReachedTransferOk) soraScanApi.getTransactionsPaged(
                myAddress,
                pageNumberSuccessful,
                countRemote,
            ) else null

            // error transfers
            var pageNumberError =
                ceil(
                    transactionDao.countTransferNotLocalError().toFloat() / state.config.pageSize
                ).toLong() + 1
            if (loadType == LoadType.REFRESH) pageNumberError = 1
            val responseTransfersError =
                if (!endReachedTransferError) soraScanApi.getAssetsTransfersErrorPaged(
                    myAddress,
                    pageNumberError,
                    countRemote,
                ) else null

            // swap extrinsic
            var pageNumberSwap =
                ceil(
                    transactionDao.countSwapNotLocal().toFloat() / state.config.pageSize
                ).toLong() + 1
            if (loadType == LoadType.REFRESH) pageNumberSwap = 1
            val responseSwap = if (!endReachedSwap) soraScanApi.getLiquiditySwapPaged(
                myAddress,
                pageNumberSwap,
                countRemote,
            ).data.map {
                val detailsResponse =
                    soraScanApi.getSwapExtrinsicDetails(it.attributes.extrinsic_hash)
                Pair(it, detailsResponse)
            } else null

            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    transactionDao.clearNotLocal()
                }
                responseTransfersOk?.let {
                    val mapped = mapRemoteTransfersToLocal(it.data, myAddress, tokens)
                    transactionDao.insert(mapped.first)
                    transactionDao.insertParams(mapped.second)
                }
                responseTransfersError?.let {
                    val mapped = mapRemoteErrorTransfersToLocal(it.data, tokens)
                    transactionDao.insert(mapped.first)
                    transactionDao.insertParams(mapped.second)
                }
                responseSwap?.let {
                    val mapped = SwapRemoteMapper.mapSwapRemoteToLocal(it, tokens)
                    transactionDao.insert(mapped.first)
                    transactionDao.insertParams(mapped.second)
                }
            }
            endReachedTransferOk =
                (responseTransfersOk?.data?.size ?: 0 < countRemote) || (responseTransfersOk?.errors?.size ?: 0 > 0)
            endReachedTransferError =
                (responseTransfersError?.data?.size ?: 0 < countRemote) || (responseTransfersError?.errors?.size ?: 0 > 0)
            endReachedSwap =
                (responseSwap?.size ?: 0 < countRemote) || (responseSwap?.size ?: 0 > 0)

            MediatorResult.Success(endOfPaginationReached = endReachedTransferError && endReachedTransferOk && endReachedSwap)
        } catch (e: Throwable) {
            MediatorResult.Error(e)
        }
    }

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }
}
