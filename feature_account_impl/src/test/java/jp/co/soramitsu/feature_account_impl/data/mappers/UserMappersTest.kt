package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_account_impl.data.network.model.UserRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.UserValuesRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class UserMappersTest {

    @Test
    fun `map user remote to user reputation called`() {
        val userRemote = UserRemote(
            "1",
            "firstname",
            "lastName",
            "phone",
            "status",
            "parentId",
            "country",
            1,
            UserValuesRemote(
                "1",
                "invitationCode"
            )
        )
        val user = User(
            "1",
            "firstname",
            "lastName",
            "phone",
            "status",
            "parentId",
            "country",
            1000,
            UserValues(
                "invitationCode",
                "1"
            )
        )

        assertEquals(user, mapUserRemoteToUser(userRemote))
    }

    @Test fun `map User Values Remote To User Values called`() {
        val userValuesRemote = UserValuesRemote(
            "1",
            "invitationCode"
        )

        val userValues = UserValues(
            "invitationCode",
            "1"
        )

        assertEquals(userValues, mapUserValuesRemoteToUserValues(userValuesRemote))
    }
}