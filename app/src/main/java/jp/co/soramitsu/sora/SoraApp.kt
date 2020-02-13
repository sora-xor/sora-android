/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora

import android.content.Context
import android.content.res.Configuration
import androidx.multidex.MultiDexApplication
import com.crashlytics.android.Crashlytics
import com.google.firebase.FirebaseApp
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import io.fabric.sdk.android.Fabric
import io.reactivex.internal.functions.Functions.emptyConsumer
import io.reactivex.plugins.RxJavaPlugins
import jp.co.soramitsu.common.logger.DiskLoggerAdapter
import jp.co.soramitsu.common.logger.LoggerAdapter
import jp.co.soramitsu.common.resourses.ContextManagerImpl
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.LanguagesHolderImpl
import jp.co.soramitsu.core_di.holder.FeatureContainer
import jp.co.soramitsu.core_di.holder.FeatureHolderManager
import jp.co.soramitsu.sora.di.app.DaggerAppComponent
import javax.inject.Inject

const val TAG = "SORA"

class SoraApp : MultiDexApplication(), FeatureContainer {

    @Inject lateinit var featureHolderManager: FeatureHolderManager

    private val languagesHolder: LanguagesHolder = LanguagesHolderImpl()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManagerImpl.getInstanceOrInit(base, languagesHolder)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val contextManager = ContextManagerImpl.getInstanceOrInit(this, languagesHolder)
        contextManager.setLocale(this)
    }

    override fun onCreate() {
        super.onCreate()
        val contextManger = ContextManagerImpl.getInstanceOrInit(this, languagesHolder)

        DaggerAppComponent
            .builder()
            .application(this)
            .contextManager(contextManger)
            .build()
            .inject(this)

        initLogger()

        FirebaseApp.initializeApp(this)
        Fabric.with(this, Crashlytics())
        RxJavaPlugins.setErrorHandler(emptyConsumer())
    }

    private fun initLogger() {
        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .tag(TAG)
            .build()

        Logger.addLogAdapter(LoggerAdapter(formatStrategy))
        Logger.addLogAdapter(DiskLoggerAdapter())
    }

    override fun <T> getFeature(key: Class<*>): T {
        return featureHolderManager.getFeature<T>(key)!!
    }

    override fun releaseFeature(key: Class<*>) {
        featureHolderManager.releaseFeature(key)
    }
}