package jp.co.soramitsu.core_db.di

import android.content.Context
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.core_db.AppDatabase
import javax.inject.Singleton

@Module
class DbModule {

    @Provides
    @Singleton
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase.get(context)
    }
}