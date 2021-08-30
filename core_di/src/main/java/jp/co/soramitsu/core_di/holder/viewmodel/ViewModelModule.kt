package jp.co.soramitsu.core_di.holder.viewmodel

import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun bindViewModelFactory(factory: SoraViewModelFactory): ViewModelProvider.Factory
}
