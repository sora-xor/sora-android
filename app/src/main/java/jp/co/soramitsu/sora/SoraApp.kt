/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora

import android.content.Context
import android.content.res.Configuration
import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import jp.co.soramitsu.common.data.network.NetworkApi
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.di.api.CommonApi
import jp.co.soramitsu.common.di.api.FeatureContainer
import jp.co.soramitsu.common.logger.DiskLoggerAdapter
import jp.co.soramitsu.common.logger.LoggerAdapter
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.sora.di.app.AppComponent
import jp.co.soramitsu.sora.di.app.DaggerAppComponent
import jp.co.soramitsu.sora.di.app.FeatureHolderManager
import javax.inject.Inject

const val TAG = "SORA"

class SoraApp : MultiDexApplication(), FeatureContainer {

    @Inject
    lateinit var featureHolderManager: FeatureHolderManager

    @Inject
    lateinit var connectionManager: ConnectionManager

    private lateinit var appComponent: AppComponent

    private val languagesHolder: LanguagesHolder = LanguagesHolder()

    override fun attachBaseContext(base: Context) {
        val contextManager = ContextManager.getInstanceOrInit(base, languagesHolder)
        super.attachBaseContext(contextManager.setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val contextManager = ContextManager.getInstanceOrInit(this, languagesHolder)
        contextManager.setLocale(this)
    }

    override fun onCreate() {
        super.onCreate()
        val contextManger = ContextManager.getInstanceOrInit(this, languagesHolder)

        appComponent = DaggerAppComponent
            .builder()
            .application(this)
            .contextManager(contextManger)
            .build()

        appComponent.inject(this)

        initLogger()

        FirebaseApp.initializeApp(this)

        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
    }

    private fun initLogger() {
        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .tag(TAG)
            .build()

        Logger.addLogAdapter(LoggerAdapter(formatStrategy))
        applicationContext.externalCacheDir?.absolutePath?.let {
            Logger.addLogAdapter(DiskLoggerAdapter(it))
        }
    }

    override fun <T> getFeature(key: Class<*>): T {
        return featureHolderManager.getFeature<T>(key)!!
    }

    override fun releaseFeature(key: Class<*>) {
        featureHolderManager.releaseFeature(key)
    }

    override fun commonApi(): CommonApi {
        return appComponent
    }

    override fun networkApi(): NetworkApi {
        return appComponent
    }
}
