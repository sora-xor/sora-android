/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
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

    @Inject
    lateinit var darkThemeManager: DarkThemeManager

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

    override fun onCreate() {
        super.onCreate()

        initLogger()

        registerActivityLifecycleCallbacks(resourceManager)
        FirebaseApp.initializeApp(this)

        OptionsProvider.CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        OptionsProvider.CURRENT_VERSION_NAME = BuildConfig.VERSION_NAME
        OptionsProvider.APPLICATION_ID = BuildConfig.APPLICATION_ID

        darkThemeManager.updateUiModeFromCache()
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
