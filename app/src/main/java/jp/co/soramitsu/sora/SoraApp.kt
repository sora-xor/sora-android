/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.google.GoogleEmojiProvider
import dagger.hilt.android.HiltAndroidApp
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.util.BuildType
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
open class SoraApp : MultiDexApplication(), Configuration.Provider {

    @Inject
    lateinit var connectionManager: ConnectionManager

    @Inject
    lateinit var fileManager: FileManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ContextManager.setLocale(base))
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ContextManager.setLocale(this)
    }

    override fun onCreate() {
        super.onCreate()

        initLogger()

        FirebaseApp.initializeApp(this)

        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.APPLICATION_ID = BuildConfig.APPLICATION_ID
        EmojiManager.install(GoogleEmojiProvider())
    }

    private fun initLogger() {
        if (BuildUtils.isBuildType(BuildType.FIREBASE, BuildType.DEBUG)) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "SORA_LOG: ${Thread.currentThread().name}"
                }
            })
            try {
                Runtime.getRuntime().exec("logcat -f ${fileManager.logStorageDir} -v threadtime -r ${1024 * 4} -n 5")
            } catch (t: Throwable) {
                Timber.e(t)
            }
            Timber.d("logger has been started ${BuildConfig.BUILD_TYPE} ${BuildConfig.FLAVOR} ${BuildConfig.VERSION_NAME}")
        }
    }
}
