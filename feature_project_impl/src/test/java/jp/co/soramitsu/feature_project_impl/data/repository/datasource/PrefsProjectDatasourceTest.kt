/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefsProjectDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var preferences: Preferences

    private lateinit var prefsProjectDatasource: PrefsProjectDatasource

    @Before fun setUp() {
        prefsProjectDatasource = PrefsProjectDatasource(preferences)
    }

    @Test fun `save votes called`() {
        val keyVotes = "key_votes"
        val votes = "1"

        prefsProjectDatasource.saveVotes(votes)

        verify(preferences).putString(keyVotes, votes)
    }

    @Test fun `retrieve votes called`() {
        val keyVotes = "key_votes"
        val votes = "1"
        given(preferences.getString(keyVotes)).willReturn(votes)

        assertEquals(votes, prefsProjectDatasource.retrieveVotes())
    }

    @Test fun `save last receiver votes called`() {
        val keyLastVotes = "key_projects_last_voted"
        val votes = "1"

        prefsProjectDatasource.saveLastReceivedVotes(votes)

        verify(preferences).putString(keyLastVotes, votes)
    }

    @Test fun `retrieve last receiver votes called`() {
        val keyLastVotes = "key_projects_last_voted"
        val votes = "1"
        given(preferences.getString(keyLastVotes)).willReturn(votes)

        assertEquals(votes, prefsProjectDatasource.retrieveLastReceivedVotes())
    }
}
