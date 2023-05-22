/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import kotlinx.coroutines.flow.Flow

interface SingleFeatureStorageManager {
    fun subscribe(address: String): Flow<String>
}
