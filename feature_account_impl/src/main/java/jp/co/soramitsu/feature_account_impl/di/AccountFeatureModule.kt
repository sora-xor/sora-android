package jp.co.soramitsu.feature_account_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_impl.data.network.AccountNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.ActivityFeedNetworkApi
import jp.co.soramitsu.feature_account_impl.data.repository.UserRepositoryImpl
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.PrefsUserDatasource
import javax.inject.Singleton

@Module
class AccountFeatureModule {

    @Provides
    @Singleton
    fun provideUserRepository(userRepository: UserRepositoryImpl): UserRepository = userRepository

    @Provides
    @Singleton
    fun provideAccountNetworkApiApi(apiCreator: NetworkApiCreator): AccountNetworkApi {
        return apiCreator.create(AccountNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideActivityFeedNetworkApi(apiCreator: NetworkApiCreator): ActivityFeedNetworkApi {
        return apiCreator.create(ActivityFeedNetworkApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserDatasource(userDatasourceImpl: PrefsUserDatasource): UserDatasource = userDatasourceImpl
}