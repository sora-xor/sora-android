package jp.co.soramitsu.feature_main_impl.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import javax.inject.Inject

class InvitationInteractor @Inject constructor(
    private val userRepository: UserRepository,
) {

    suspend fun getInviteLink(): String {
        return userRepository.getInvitationLink()
    }
}
