/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.di

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.app.ScreenScope
import jp.co.soramitsu.sora.splash.domain.SplashRouter
import jp.co.soramitsu.sora.splash.presentation.SplashActivity

@Subcomponent(
    modules = [
        SplashModule::class
    ]
)
@ScreenScope
interface SplashComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun withActivity(fragment: AppCompatActivity): Builder

        @BindsInstance
        fun withRouter(splashRouter: SplashRouter): Builder

        fun build(): SplashComponent
    }

    fun inject(splashActivity: SplashActivity)
}