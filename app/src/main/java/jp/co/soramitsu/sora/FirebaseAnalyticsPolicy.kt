package jp.co.soramitsu.sora

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType

/** App-wide metrics policy; no wallet identity, secrets, or custom event payloads are sent. */
internal object FirebaseAnalyticsPolicy {
    fun apply(analytics: FirebaseAnalytics) {
        // Runtime consent persists and can override manifest defaults. Reapply all four values
        // on every process start, then enable metrics after advertising restrictions are set.
        analytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to ConsentStatus.GRANTED,
                ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
            )
        )
        analytics.setUserProperty(FirebaseAnalytics.UserProperty.ALLOW_AD_PERSONALIZATION_SIGNALS, "false")
        analytics.setAnalyticsCollectionEnabled(true)
    }
}
