/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_votable_api.domain.model.referendum.Referendum

interface ReferendumRepository {
    fun observeReferendum(referendumId: String): Observable<Referendum>

    fun observeOpenedReferendums(): Observable<List<Referendum>>

    fun observeVotedReferendums(): Observable<List<Referendum>>

    fun observeFinishedReferendums(): Observable<List<Referendum>>
}
