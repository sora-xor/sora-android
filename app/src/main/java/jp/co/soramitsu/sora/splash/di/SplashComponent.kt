package jp.co.soramitsu.sora.splash.di

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.core_di.holder.scope.ScreenScope
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import jp.co.soramitsu.sora.splash.presentation.SplashActivity

@Subcomponent(
    modules = [
        SplashModule::class
    ]
)
@ScreenScope
interface SplashComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withActivity(fragment: AppCompatActivity): Builder

        @BindsInstance
        fun withRouter(splashRouter: SplashRouter): Builder

        fun build(): SplashComponent
    }

    fun inject(splashActivity: SplashActivity)
}
