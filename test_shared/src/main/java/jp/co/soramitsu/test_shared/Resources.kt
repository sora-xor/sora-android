/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.test_shared

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader

fun Any.getFileContentFromResources(fileName: String): String {
    return getResourceReader(fileName).readText()
}

fun Any.getResourceReader(fileName: String): Reader {
    val stream = javaClass.classLoader!!.getResourceAsStream(fileName)

    return BufferedReader(InputStreamReader(stream))
}
