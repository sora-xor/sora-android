/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceManagerImpl @Inject constructor(
    private val context: Context
) : ResourceManager {

    override fun getString(resource: Int): String {
        return context.getString(resource)
    }
}