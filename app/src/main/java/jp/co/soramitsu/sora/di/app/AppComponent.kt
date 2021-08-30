package jp.co.soramitsu.sora.di.app

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.common.di.modules.CommonModule
import jp.co.soramitsu.common.di.modules.NetworkModule
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.sora.SoraApp
import jp.co.soramitsu.sora.di.app.modules.AppModule
import jp.co.soramitsu.sora.di.app.modules.ComponentHolderModule
import jp.co.soramitsu.sora.di.app.modules.FeatureManagerModule
import jp.co.soramitsu.sora.di.app.modules.NavigationModule
import javax.inject.Singleton

@Component(
    modules = [
        AppModule::class,
        NavigationModule::class,
        ComponentHolderModule::class,
        FeatureManagerModule::class,
        CommonModule::class,
        NetworkModule::class
    ]
)
@Singleton
interface AppComponent : CommonApi, NetworkApi {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun application(application: SoraApp): Builder

        @BindsInstance
        fun contextManager(contextManager: ContextManager): Builder

        fun build(): AppComponent
    }

    fun inject(app: SoraApp)
}
