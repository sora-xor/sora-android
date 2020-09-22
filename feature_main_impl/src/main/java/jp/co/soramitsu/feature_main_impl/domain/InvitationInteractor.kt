/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import jp.co.soramitsu.feature_account_api.domain.model.User
import javax.inject.Inject

class InvitationInteractor @Inject constructor(
    private val userRepository: UserRepository
) {

    fun getUserInviteInfo(updateCached: Boolean): Single<Pair<User, Invitations>> {
        return zipUserAndInvites(updateCached)
            .subscribeOn(Schedulers.io())
    }

    private fun zipUserAndInvites(updateCached: Boolean): Single<Pair<User, Invitations>> {
        return Single.zip(
            userRepository.getUser(updateCached),
            userRepository.getInvitedUsers(updateCached),
            BiFunction { user, invited -> Pair(user, invited) }
        )
    }

    fun getInviteLink(): Single<String> {
        return userRepository.getInvitationLink()
    }

    fun updateInvitationInfo(): Single<Invitations> {
        return zipUserAndInvites(true)
            .map { it.second }
            .subscribeOn(Schedulers.io())
    }

    fun enterInviteCode(inviteCode: String): Single<Invitations> {
        return userRepository.enterInviteCode(inviteCode)
            .andThen(userRepository.getInvitedUsers(true))
    }
}