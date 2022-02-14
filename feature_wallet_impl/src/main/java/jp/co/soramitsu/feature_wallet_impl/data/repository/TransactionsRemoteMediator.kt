/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.gson.Gson
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapRemoteTransfersToLocal
import jp.co.soramitsu.feature_wallet_impl.data.network.request.HistoryBodyRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import kotlinx.coroutines.CompletableDeferred

@ExperimentalPagingApi
class TransactionsRemoteMediator(
    private val soraScanApi: SoraScanApi,
    private val db: AppDatabase,
    private val myAddress: String,
    private val tokensDeferred: CompletableDeferred<List<Token>>,
    private val gson: Gson
) : RemoteMediator<Int, ExtrinsicLocal>() {

    private val transactionDao = db.transactionDao()
    private var endReached = false
    private var cursor: String = ""

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
                endReached = false
            }

            if (loadType == LoadType.REFRESH) {
                cursor = ""
            }
            val response = if (!endReached) soraScanApi.getHistory(
                HistoryBodyRequest(
                    """
                    query { 
                      historyElements( 
                        first: $countRemote 
                        orderBy: TIMESTAMP_DESC 
                        after: "$cursor" 
                        filter: { 
                          or: [ 
                            { 
                              address: { equalTo: "$myAddress" } 
                              or: [ 
                                { module: { equalTo: "assets" } method: { equalTo: "transfer" } } 
                                { 
                                  module: { equalTo: "liquidityProxy" } 
                                  method: { equalTo: "swap" } 
                                } 
                              ] 
                            } 
                            { 
                              data: { contains: { to: "$myAddress" } } 
                              execution: { contains: { success: true } }
                            } 
                          ] 
                        } 
                      ) { 
                        nodes { 
                          id
                          blockHash
                          module
                          method
                          address
                          networkFee
                          execution
                          timestamp
                          data 
                        }
                        pageInfo {
                          endCursor
                          hasNextPage
                        }
                      } 
                    }
                """
                )
            ) else null
            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    transactionDao.clearNotLocal()
                }
                response?.let {
                    val mapped = mapRemoteTransfersToLocal(it.data.historyElements.nodes, myAddress, tokens, gson)
                    transactionDao.insert(mapped.first)
                    transactionDao.insertParams(mapped.second)
                }
            }
            cursor = response?.data?.historyElements?.pageInfo?.endCursor.orEmpty()
            endReached = (response?.data?.historyElements?.pageInfo?.hasNextPage != true)
            MediatorResult.Success(endOfPaginationReached = endReached)
        } catch (e: Throwable) {
            FirebaseWrapper.recordException(e)
            MediatorResult.Error(e)
        }
    }

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }
}
