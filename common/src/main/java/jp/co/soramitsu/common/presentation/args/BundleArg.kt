/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.args

import android.os.Bundle

const val BUNDLE_KEY = "BUNDLE_KEY"

fun Bundle.requireString(key: String): String =
    this.getString(key) ?: throw IllegalArgumentException("Argument with key $key is null")
