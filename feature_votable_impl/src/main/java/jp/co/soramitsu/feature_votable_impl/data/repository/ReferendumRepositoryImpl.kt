/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ReferendumLocal
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ReferendumRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum
import jp.co.soramitsu.feature_votable_impl.data.mappers.toReferendum
import jp.co.soramitsu.feature_votable_impl.data.mappers.toReferendumLocal
import jp.co.soramitsu.feature_votable_impl.data.network.ReferendumNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetReferendumDetailsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetReferendumsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.VoteResponse
import javax.inject.Inject

class ReferendumRepositoryImpl @Inject constructor(
    private val api: ReferendumNetworkApi,
    private val votesDataSource: VotesDataSource,
    db: AppDatabase
) : ReferendumRepository {
    private val dbDao = db.referendumDao()

    override fun observeReferendum(referendumId: String): Observable<Referendum> {
        return dbDao.observeReferendum(referendumId)
            .map(ReferendumLocal::toReferendum)
    }

    override fun syncReferendum(referendumId: String): Completable {
        return api.getReferendumDetails(referendumId)
            .map(GetReferendumDetailsResponse::referendum)
            .map(ReferendumRemote::toReferendum)
            .doOnSuccess { dbDao.insert(it.toReferendumLocal()) }
            .ignoreElement()
    }

    override fun observeOpenedReferendums(): Observable<List<Referendum>> {
        return dbDao.observeOpenReferendums()
            .map(::convertFromLocal)
    }

    override fun syncOpenedReferendums(): Completable {
        return api.getOpenedReferendums()
            .insertToDb()
    }

    override fun observeVotedReferendums(): Observable<List<Referendum>> {
        return dbDao.observeVotedReferendums()
            .map(::convertFromLocal)
    }

    override fun syncVotedReferendums(): Completable {
        return api.getVotedReferendums()
            .insertToDb()
    }

    override fun observeFinishedReferendums(): Observable<List<Referendum>> {
        return dbDao.observeFinishedReferendums()
            .map(::convertFromLocal)
    }

    override fun syncFinishedReferendums(): Completable {
        return api.getFinishedReferendums()
            .insertToDb()
    }

    override fun voteForReferendum(referendumId: String, voteCount: Long): Completable {
        return api.voteForReferendum(referendumId, voteCount)
            .putUpdateToDb()
    }

    override fun voteAgainstReferendum(referendumId: String, voteCount: Long): Completable {
        return api.voteAgainstReferendum(referendumId, voteCount)
            .putUpdateToDb()
    }

    private fun Single<VoteResponse>.putUpdateToDb() =
        doOnSuccess {
            val votes = it.votesRemain
            votesDataSource.saveVotes(votes)

            val referendumLocal = it.referendum.toReferendum().toReferendumLocal()
            dbDao.update(referendumLocal)
        }.ignoreElement()

    private fun Single<GetReferendumsResponse>.insertToDb(): Completable {
        return map(GetReferendumsResponse::referendums)
            .map(::convertFromRemote)
            .doOnSuccess { dbDao.insert(*prepareForLocalInsertion(it)) }
            .ignoreElement()
    }
}

private fun convertFromLocal(list: List<ReferendumLocal>) = list.map(ReferendumLocal::toReferendum)

private fun prepareForLocalInsertion(list: List<Referendum>) =
    list.map(Referendum::toReferendumLocal)
        .toTypedArray()

private fun convertFromRemote(list: List<ReferendumRemote>) =
    list.map(ReferendumRemote::toReferendum)