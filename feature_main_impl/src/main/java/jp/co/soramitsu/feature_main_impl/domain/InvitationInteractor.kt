/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import javax.inject.Inject

class InvitationInteractor @Inject constructor(
    private val userRepository: UserRepository,
) {

    fun getInviteLink(): Single<String> {
        return userRepository.getInvitationLink()
    }
}
