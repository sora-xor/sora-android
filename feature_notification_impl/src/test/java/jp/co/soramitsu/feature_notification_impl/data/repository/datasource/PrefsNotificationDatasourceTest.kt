package jp.co.soramitsu.feature_notification_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.util.Const.Companion.DEVICE_TOKEN
import jp.co.soramitsu.common.util.Const.Companion.IS_PUSH_UPDATE_NEEDED
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefsNotificationDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var encryptedPreferences: EncryptedPreferences
    @Mock private lateinit var serializer: Serializer

    private lateinit var prefsNotificationDatasource: PrefsNotificationDatasource

    @Before fun setUp() {
        prefsNotificationDatasource = PrefsNotificationDatasource(preferences, encryptedPreferences)
    }

    @Test fun `save pushToken calls prefsutil putEncryptedString for DEVICE_TOKEN`() {
        val notificationToken = "1234"

        prefsNotificationDatasource.savePushToken(notificationToken)

        verify(encryptedPreferences).putEncryptedString(DEVICE_TOKEN, notificationToken)
    }

    @Test fun `retrieve pushToken calls prefsutil getDecryptedString for DEVICE_TOKEN`() {
        prefsNotificationDatasource.retrievePushToken()

        verify(encryptedPreferences).getDecryptedString(DEVICE_TOKEN)
    }

    @Test fun `save IsPushTokenUpdateNeeded calls prefsutil putBoolean for IS_PUSH_UPDATE_NEEDED`() {
        val isUpdateNeeded = true

        prefsNotificationDatasource.saveIsPushTokenUpdateNeeded(isUpdateNeeded)

        verify(preferences).putBoolean(IS_PUSH_UPDATE_NEEDED, isUpdateNeeded)
    }

    @Test fun `retrieve IsPushTokenUpdateNeeded calls prefsutil getBoolean for DEVICE_TOKEN`() {
        prefsNotificationDatasource.isPushTokenUpdateNeeded()

        verify(preferences).getBoolean(IS_PUSH_UPDATE_NEEDED)
    }
}