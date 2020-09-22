/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum

interface ReferendumRepository {
    fun observeReferendum(referendumId: String): Observable<Referendum>
    fun syncReferendum(referendumId: String): Completable

    fun observeOpenedReferendums(): Observable<List<Referendum>>
    fun syncOpenedReferendums(): Completable

    fun observeVotedReferendums(): Observable<List<Referendum>>
    fun syncVotedReferendums(): Completable

    fun observeFinishedReferendums(): Observable<List<Referendum>>
    fun syncFinishedReferendums(): Completable

    fun voteForReferendum(referendumId: String, voteCount: Long): Completable
    fun voteAgainstReferendum(referendumId: String, voteCount: Long): Completable
}