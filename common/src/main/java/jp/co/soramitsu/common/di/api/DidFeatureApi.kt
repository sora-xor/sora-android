/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.api

import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.util.DidProvider

interface DidFeatureApi {

    fun providesDidRepository(): DidRepository

    fun provideDidProvider(): DidProvider
}