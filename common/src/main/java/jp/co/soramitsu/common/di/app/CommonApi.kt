/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.app

import android.content.Context
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.PrefsUtil

interface CommonApi {

    fun context(): Context

    fun prefsUtil(): PrefsUtil

    fun resourceManager(): ResourceManager

    fun appVersionProvider(): AppVersionProvider

    fun numbersFormatter(): NumbersFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker
}