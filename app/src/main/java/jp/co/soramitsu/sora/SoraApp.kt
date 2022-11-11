/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
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
import jp.co.soramitsu.feature_select_node_api.NodeManager
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
open class SoraApp : Application(), Configuration.Provider {

    @Inject
    lateinit var nodeManager: NodeManager

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
                Runtime.getRuntime()
                    .exec("logcat -f ${fileManager.logStorageDir} -v threadtime -r ${1024 * 4} -n 5")
            } catch (t: Throwable) {
                Timber.e(t)
            }
            Timber.d("logger has been started ${BuildConfig.BUILD_TYPE} ${BuildConfig.FLAVOR} ${BuildConfig.VERSION_NAME}")
        }
    }
}
