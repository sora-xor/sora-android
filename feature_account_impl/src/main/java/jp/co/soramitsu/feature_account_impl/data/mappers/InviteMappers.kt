package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_impl.data.network.model.InvitedRemote

fun mapInvitedDtoToInvitedUser(invitedRemote: InvitedRemote?): InvitedUser? {
    return if (invitedRemote == null) {
        null
    } else {
        InvitedUser(invitedRemote.firstName, invitedRemote.lastName)
    }
}