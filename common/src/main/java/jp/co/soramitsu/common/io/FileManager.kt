/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.io

interface FileManager {
    val internalCacheDir: String
    fun readAssetFile(fileName: String): String
}
