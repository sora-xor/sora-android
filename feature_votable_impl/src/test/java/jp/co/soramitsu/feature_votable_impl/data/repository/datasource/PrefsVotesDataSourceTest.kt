package jp.co.soramitsu.feature_votable_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.network.ProjectNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectVotesResponse
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.BDDMockito.verifyZeroInteractions
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class PrefsVotesDataSourceTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var preferences: Preferences

    @Mock
    private lateinit var networkApi: ProjectNetworkApi

    private lateinit var prefsProjectDataSource: PrefsVotesDataSource

    @Before
    fun setUp() {
        given(preferences.getString(anyString())).willReturn("0")

        val votes = BigDecimal.TEN
        val status = StatusDto("OK", "Success")

        given(networkApi.getVotes()).willReturn(Single.just(GetProjectVotesResponse(votes, votes, status)))

        prefsProjectDataSource = PrefsVotesDataSource(networkApi, preferences)
    }

    @Test
    fun `observed votes should be triggered after sync`() {
        var syncHappened = false

        prefsProjectDataSource.observeVotes().subscribe {
            if (syncHappened) {
                assertEquals("10", it)
            }
        }

        syncHappened = true

        prefsProjectDataSource.syncVotes()
    }

    @Test
    fun `observed votes should be triggered with cache value if it exists`() {
        given(preferences.contains(anyString())).willReturn(true)

        prefsProjectDataSource.observeVotes().subscribe {
            assertEquals("0", it)
        }

        verify(networkApi, never()).getVotes()
    }

    @Test
    fun `observed votes should be taken from network if no cache is found`() {
        given(preferences.contains(anyString())).willReturn(false)

        prefsProjectDataSource.observeVotes().subscribe {
            assertEquals("10", it)
        }

        verify(networkApi).getVotes()
    }

    @Test
    fun `save votes called`() {
        val keyVotes = "key_votes"
        val votes = "1"

        prefsProjectDataSource.saveVotes(votes)

        verify(preferences).putString(keyVotes, votes)
    }

    @Test
    fun `retrieve votes called`() {
        val keyVotes = "key_votes"
        val votes = "1"
        given(preferences.getString(keyVotes)).willReturn(votes)

        assertEquals(votes, prefsProjectDataSource.retrieveVotes())
    }

    @Test
    fun `save last receiver votes called`() {
        val keyLastVotes = "key_projects_last_voted"
        val votes = "1"

        prefsProjectDataSource.saveLastReceivedVotes(votes)

        verify(preferences).putString(keyLastVotes, votes)
    }

    @Test
    fun `retrieve last receiver votes called`() {
        val keyLastVotes = "key_projects_last_voted"
        val votes = "1"
        given(preferences.getString(keyLastVotes)).willReturn(votes)

        assertEquals(votes, prefsProjectDataSource.retrieveLastReceivedVotes())
    }
}
