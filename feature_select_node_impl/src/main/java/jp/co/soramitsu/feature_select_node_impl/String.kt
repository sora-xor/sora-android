/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl

internal fun String.compareWithUrl(url: String): Boolean {
    return url == this || "$url/" == this || "$this/" == url
}
