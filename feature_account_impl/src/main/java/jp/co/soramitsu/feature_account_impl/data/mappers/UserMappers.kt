package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_account_impl.data.network.model.UserRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.UserValuesRemote

fun mapUserRemoteToUser(userRemote: UserRemote): User {
    return with(userRemote) {
        User(
            userId,
            firstName,
            lastName,
            phone,
            status,
            parentId ?: "",
            country ?: "",
            inviteAcceptExpirationMomentSeconds * 1000,
            mapUserValuesRemoteToUserValues(userValues)
        )
    }
}

fun mapUserValuesRemoteToUserValues(userValuesDto: UserValuesRemote): UserValues {
    return with(userValuesDto) {
        UserValues(invitationCode, userId)
    }
}