package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_impl.data.network.model.ReputationRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ReputationMappersTest {

    @Test
    fun `map reputation remote to reputation called`() {
        val reputationRemote = ReputationRemote(
            1,
            2f,
            3
        )
        val reputation = Reputation(
            1,
            2f,
            3
        )

        assertEquals(reputation, mapReputationRemoteToReputation(reputationRemote))
    }
}