/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import androidx.fragment.app.Fragment

fun <T> Fragment.argument(name: String): T {
    return arguments?.get(name) as? T ?: throw IllegalStateException("Argument $name not found.")
}