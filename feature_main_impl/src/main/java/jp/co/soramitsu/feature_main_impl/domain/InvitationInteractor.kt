/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import javax.inject.Inject

class InvitationInteractor @Inject constructor(
    private val userRepository: UserRepository
) {

    fun getInvitedUsers(updateCached: Boolean): Single<Invitations> {
        return userRepository.getInvitedUsers(updateCached)
    }

    fun getInvitationsLeft(updateCached: Boolean): Single<Int> {
        return userRepository.getUserValues(updateCached)
    }

    fun sendInviteCode(): Single<Pair<String, Int>> {
        return userRepository.getInvitationLink()
            .flatMap { inviteLink ->
                userRepository.getUserValues(true)
                    .map { Pair(inviteLink, it) }
            }
    }
}