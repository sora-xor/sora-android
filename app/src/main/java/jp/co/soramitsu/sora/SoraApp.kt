/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.BuildType
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.feature_select_node_api.NodeManager
import timber.log.Timber

@HiltAndroidApp
open class SoraApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var nodeManager: NodeManager

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var fileManager: FileManager

    @Inject
    lateinit var svg: SvgDecoder.Factory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun newImageLoader(): ImageLoader {
        val loader = ImageLoader.Builder(this).components {
            add(svg)
        }
        return loader.build()
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        initLogger()

        registerActivityLifecycleCallbacks(resourceManager)
        FirebaseApp.initializeApp(this)

        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.APPLICATION_ID = BuildConfig.APPLICATION_ID
    }

    private fun initLogger() {
        if (BuildUtils.isBuildType(BuildType.FIREBASE, BuildType.DEBUG) || BuildUtils.isFlavors(Flavor.DEVELOP)) {
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
