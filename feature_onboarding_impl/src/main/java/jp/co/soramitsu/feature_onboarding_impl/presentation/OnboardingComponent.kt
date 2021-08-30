package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope

@Subcomponent(
    modules = [
        OnboardingModule::class
    ]
)
@ScreenScope
interface OnboardingComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withActivity(activity: AppCompatActivity): Builder

        fun build(): OnboardingComponent
    }

    fun inject(onboardingActivity: OnboardingActivity)
}
