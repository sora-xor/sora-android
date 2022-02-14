/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.logger

import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.FormatStrategy
import jp.co.soramitsu.common.BuildConfig

class LoggerAdapter(strategy: FormatStrategy) : AndroidLogAdapter(strategy) {

    override fun isLoggable(priority: Int, tag: String?): Boolean {
        return BuildConfig.DEBUG
    }
}
