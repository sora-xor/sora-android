package jp.co.soramitsu.feature_votable_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.ProjectDetailsDao
import jp.co.soramitsu.core_db.dao.ReferendumDao
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import jp.co.soramitsu.feature_votable_impl.data.mappers.toReferendum
import jp.co.soramitsu.feature_votable_impl.data.mappers.toReferendumLocal
import jp.co.soramitsu.feature_votable_impl.data.network.ReferendumNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.model.ReferendumRemote
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetReferendumsResponse
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.BDDMockito.verify
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class ReferendumRepositoryTest {
    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var networkApi: ReferendumNetworkApi

    @Mock
    private lateinit var dataSource: PrefsVotesDataSource

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var referendumDao: ReferendumDao


    private lateinit var repository: ReferendumRepositoryImpl

    private val referendumRemote = ReferendumRemote(
        "123", "123", System.currentTimeMillis(), "123", "123",
        "123", "CREATED", 123, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)

    private val referendum = referendumRemote.toReferendum()

    private val referendumLocal = referendum.toReferendumLocal()

    private val referendumsList = listOf(referendum)

    @Before
    fun setup() {
        given(db.referendumDao()).willReturn(referendumDao)

        given(referendumDao.observeFinishedReferendums()).willReturn(Observable.just(listOf(referendumLocal)))
        given(referendumDao.observeOpenReferendums()).willReturn(Observable.just(listOf(referendumLocal)))
        given(referendumDao.observeVotedReferendums()).willReturn(Observable.just(listOf(referendumLocal)))

        val remoteResponse = GetReferendumsResponse(listOf(referendumRemote), StatusDto("Ok", "Success"))

        given(networkApi.getFinishedReferendums()).willReturn(Single.just(remoteResponse))
        given(networkApi.getOpenedReferendums()).willReturn(Single.just(remoteResponse))
        given(networkApi.getVotedReferendums()).willReturn(Single.just(remoteResponse))

        repository = ReferendumRepositoryImpl(networkApi, dataSource, db)
    }

    @Test
    fun `Should take referendums from db`() {
        repository.observeOpenedReferendums().subscribe {
            assertEquals(referendumsList, it)
        }

        repository.observeFinishedReferendums().subscribe {
            assertEquals(referendumsList, it)
        }

        repository.observeVotedReferendums().subscribe {
            assertEquals(referendumsList, it)
        }

        verify(networkApi, never()).getFinishedReferendums()
        verify(networkApi, never()).getOpenedReferendums()
        verify(networkApi, never()).getOpenedReferendums()
    }

    @Test
    fun `Should trigger voted observer after sync`() {
        var syncHappened = false

        repository.observeVotedReferendums().subscribe {
            if (syncHappened) {
                assertEquals(referendumsList, it)
            }
        }

        syncHappened = true

        repository.syncVotedReferendums()
    }

    @Test
    fun `Should trigger opened observer after sync`() {
        var syncHappened = false

        repository.observeOpenedReferendums().subscribe {
            if (syncHappened) {
                assertEquals(referendumsList, it)
            }
        }

        syncHappened = true

        repository.syncOpenedReferendums()
    }

    @Test
    fun `Should trigger completed observer after sync`() {
        var syncHappened = false

        repository.observeFinishedReferendums().subscribe {
            if (syncHappened) {
                assertEquals(referendumsList, it)
            }
        }

        syncHappened = true

        repository.syncFinishedReferendums()
    }

    @Test
    fun `Should update db after finished referendums sync`() {
        repository.syncFinishedReferendums()
            .subscribe {
                verify(referendumDao).insert(anyNonNull())
            }
    }

    @Test
    fun `Should update db after opened referendums sync`() {
        repository.syncOpenedReferendums()
            .subscribe {
                verify(referendumDao).insert(anyNonNull())
            }
    }

    @Test
    fun `Should update db after voted referendums sync`() {
        repository.syncVotedReferendums()
            .subscribe {
                verify(referendumDao).insert(anyNonNull())
            }
    }

    @Test
    fun `Should take referendum from db`() {
        given(referendumDao.observeReferendum(anyString())).willReturn(Observable.just(referendumLocal))

        repository.observeReferendum(referendum.id)
            .subscribe {
                verify(referendumDao).observeReferendum(referendum.id)
                verify(networkApi, never()).getReferendumDetails(anyString())

                assertEquals(referendum, it)
            }
    }
}