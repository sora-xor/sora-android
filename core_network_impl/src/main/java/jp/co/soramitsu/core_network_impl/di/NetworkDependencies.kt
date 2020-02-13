/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_impl.di

import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DidProvider

interface NetworkDependencies {

    fun healthChecker(): HealthChecker

    fun resourceManager(): ResourceManager

    fun didProvider(): DidProvider
}