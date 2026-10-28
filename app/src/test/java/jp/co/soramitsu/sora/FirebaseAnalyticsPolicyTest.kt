package jp.co.soramitsu.sora

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class FirebaseAnalyticsPolicyTest {
    @Test
    fun `retained advertising grants are replaced before metrics are enabled`() {
        val analytics = mock<FirebaseAnalytics>()
        val retained = ConsentType.values().associateWith { ConsentStatus.GRANTED }.toMutableMap()
        doAnswer { invocation ->
            retained.putAll(invocation.getArgument<Map<ConsentType, ConsentStatus>>(0))
            null
        }.whenever(analytics).setConsent(any())

        FirebaseAnalyticsPolicy.apply(analytics)

        val expected = mapOf(
            ConsentType.ANALYTICS_STORAGE to ConsentStatus.GRANTED,
            ConsentType.AD_STORAGE to ConsentStatus.DENIED,
            ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
            ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
        )
        assertEquals(expected, retained)
        inOrder(analytics) {
            verify(analytics).setConsent(expected)
            verify(analytics).setUserProperty(FirebaseAnalytics.UserProperty.ALLOW_AD_PERSONALIZATION_SIGNALS, "false")
            verify(analytics).setAnalyticsCollectionEnabled(true)
        }
        verifyNoMoreInteractions(analytics)
    }

    @Test
    fun `every process start reapplies restrictions rather than trusting a one time marker`() {
        val analytics = mock<FirebaseAnalytics>()
        FirebaseAnalyticsPolicy.apply(analytics)
        FirebaseAnalyticsPolicy.apply(analytics)
        org.mockito.kotlin.verify(analytics, org.mockito.kotlin.times(2)).setConsent(any())
        org.mockito.kotlin.verify(analytics, org.mockito.kotlin.times(2)).setUserProperty(
            FirebaseAnalytics.UserProperty.ALLOW_AD_PERSONALIZATION_SIGNALS, "false"
        )
        org.mockito.kotlin.verify(analytics, org.mockito.kotlin.times(2)).setAnalyticsCollectionEnabled(true)
        verifyNoMoreInteractions(analytics)
    }
}
