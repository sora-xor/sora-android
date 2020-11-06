package jp.co.soramitsu.feature_votable_impl.data.mappers

import jp.co.soramitsu.core_db.model.VotesHistoryLocal
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_votable_impl.data.network.model.VotesHistoryRemote
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class VoteHistoryMappersTest {

    private val votesHistoryRemote = VotesHistoryRemote(
        "message",
        "0",
        BigDecimal.TEN
    )
    private val votesHistory = VotesHistory(
        "message",
        "0",
        BigDecimal.TEN
    )
    private val votesHistoryLocal = VotesHistoryLocal(
        0,
        "message",
        "0",
        BigDecimal.TEN
    )

    @Test
    fun `map votehistory remote to votehistory called`() {
        assertEquals(votesHistory, mapVotesHistoryRemoteToVotesHistory(votesHistoryRemote))
    }

    @Test
    fun `map votehistory local to votehistory called`() {
        assertEquals(votesHistory, mapVotesHistoryLocalToVotesHistory(votesHistoryLocal))
    }

    @Test
    fun `map votehistory to votehistory local called`() {
        assertEquals(votesHistoryLocal, mapVotesHistoryToVotesHistoryLocal(votesHistory))
    }
}