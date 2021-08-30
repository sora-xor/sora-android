package jp.co.soramitsu.feature_notification_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class NotificationRepositoryTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var notificationDatasource: NotificationDatasource

    private lateinit var notificationRepository: NotificationRepository

    @Before
    fun setUp() {
        notificationRepository = NotificationRepositoryImpl(
            notificationDatasource
        )
    }

    @Test
    fun `save device token called`() {
        val token = "deviceToken"

        notificationRepository.saveDeviceToken(token)

        verify(notificationDatasource).saveIsPushTokenUpdateNeeded(true)
        verify(notificationDatasource).savePushToken(token)
    }
}