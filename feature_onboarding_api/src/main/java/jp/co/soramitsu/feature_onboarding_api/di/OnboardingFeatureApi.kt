package jp.co.soramitsu.feature_onboarding_api.di

import jp.co.soramitsu.feature_onboarding_api.OnboardingStarter

interface OnboardingFeatureApi {

    fun provideOnboardingStarter(): OnboardingStarter
}