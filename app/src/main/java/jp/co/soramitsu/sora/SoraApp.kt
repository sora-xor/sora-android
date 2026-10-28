/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.sora

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.BuildType
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.sora.splash.domain.StartupTimeTrace
import timber.log.Timber

@HiltAndroidApp
open class SoraApp : Application(), Configuration.Provider, ImageLoaderFactory {

    // Started with the first activity once the wallet upgrade gate is clear, not in
    // Application.onCreate: a process that WorkManager starts in the background never opens the
    // node socket or fetches node lists.
    @Inject
    lateinit var nodeManager: dagger.Lazy<NodeManager>

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var fileManager: FileManager

    @Inject
    lateinit var svg: SvgDecoder.Factory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupTimeTrace.begin()
        DarkThemeManager.captureNewInstallDefault(this)
        // Hilt injects eager singletons before this class's onCreate body. Prepare the immutable
        // wallet backup first so no injected database consumer can race the upgrade gate.
        WalletUpgradeBackup.prepare(this)
        // Apply last run's light/dark choice before the splash is drawn (no light flash for
        // dark-theme users). Reads one tiny preferences file; never touches wallet data.
        DarkThemeManager.applyCachedNightMode(this)
    }

    override fun newImageLoader(): ImageLoader {
        val loader = ImageLoader.Builder(this).components {
            add(svg)
        }
        return loader.build()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Instantiate the expiry watcher on every app process, even when the backup screen is not reopened.
    @Inject lateinit var sensitiveClipboard: jp.co.soramitsu.common.util.SensitiveClipboard

    override fun onCreate() {
        super.onCreate()

        initLogger()

        registerActivityLifecycleCallbacks(resourceManager)
        registerActivityLifecycleCallbacks(StartNodeManagerWhenWalletReady())
        val firebaseApp = runCatching { FirebaseApp.initializeApp(this) }
            .onFailure { Timber.e(it) }
            .getOrNull()
        if (firebaseApp != null) {
            runCatching { FirebaseAnalyticsPolicy.apply(FirebaseAnalytics.getInstance(this)) }
                .onFailure { Timber.w("Firebase Analytics policy could not be applied") }
        }
        FirebaseWrapper.setCrashlyticsEnabled(firebaseApp != null)

        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.APPLICATION_ID = BuildConfig.APPLICATION_ID

        darkThemeManager.updateUiModeFromCache()
    }

    /**
     * While the upgrade gate blocks startup, the wallet database stays closed and the node cannot
     * be started. Recovery on the splash screen (Try again, Check again, Continue read-only) can
     * clear the gate without a restart, so every later activity creation or resume tries again
     * until the node connection has started.
     */
    private inner class StartNodeManagerWhenWalletReady : ActivityLifecycleCallbacks {
        private fun startWhenReady() {
            if (WalletUpgradeBackup.blockingFailure() != null) return
            if (nodeManager.get().ensureStarted()) {
                unregisterActivityLifecycleCallbacks(this)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
            startWhenReady()

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = startWhenReady()
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun initLogger() {
        val verbose = BuildUtils.isBuildType(BuildType.FIREBASE, BuildType.DEBUG) || BuildUtils.isFlavors(Flavor.DEVELOP)
        installWalletLogTree(verbose)
        if (verbose) {
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
