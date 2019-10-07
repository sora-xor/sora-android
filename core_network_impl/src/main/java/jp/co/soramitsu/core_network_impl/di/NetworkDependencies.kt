/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.di

import android.content.Context
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.resourses.ResourceManager

interface NetworkDependencies {

    fun context(): Context

    fun healthChecker(): HealthChecker

    fun resourceManager(): ResourceManager
}