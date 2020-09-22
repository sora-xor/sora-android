/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.common.util.Const.Companion.PROJECT_DID
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_notification_impl.data.network.NotificationNetworkApi
import jp.co.soramitsu.feature_notification_impl.data.network.request.TokenChangeRequest
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class NotificationRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var notificationNetworkApi: NotificationNetworkApi
    @Mock private lateinit var notificationDatasource: NotificationDatasource

    private lateinit var notificationRepository: NotificationRepository

    @Before fun setUp() {
        notificationRepository = NotificationRepositoryImpl(
            notificationNetworkApi,
            notificationDatasource
        )
    }

    @Test fun `update push token if needed called with updateNeeded false`() {
        val isPushTokenUpdateNeeded = false
        given(notificationDatasource.isPushTokenUpdateNeeded()).willReturn(isPushTokenUpdateNeeded)

        notificationRepository.updatePushTokenIfNeeded()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(notificationDatasource, times(0)).retrievePushToken()
    }

    @Test fun `update push token if needed called`() {
        val isPushTokenUpdateNeeded = true
        val token = "token"
        val tokenChangeRequest = TokenChangeRequest(token, null)
        given(notificationDatasource.isPushTokenUpdateNeeded()).willReturn(isPushTokenUpdateNeeded)
        given(notificationDatasource.retrievePushToken()).willReturn(token)
        given(notificationNetworkApi.changeToken(tokenChangeRequest)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))
        given(notificationNetworkApi.setPermissions(PROJECT_DID)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        notificationRepository.updatePushTokenIfNeeded()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(notificationDatasource).isPushTokenUpdateNeeded()
        verify(notificationDatasource).retrievePushToken()
        verify(notificationNetworkApi).changeToken(tokenChangeRequest)
        verify(notificationNetworkApi).setPermissions(PROJECT_DID)
    }

    @Test fun `save device token called`() {
        val token = "deviceToken"

        notificationRepository.saveDeviceToken(token)

        verify(notificationDatasource).saveIsPushTokenUpdateNeeded(true)
        verify(notificationDatasource).savePushToken(token)
    }
}