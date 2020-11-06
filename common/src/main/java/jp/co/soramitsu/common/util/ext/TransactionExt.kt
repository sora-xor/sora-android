/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import iroha.protocol.TransactionOuterClass
import jp.co.soramitsu.iroha.java.Utils
import javax.xml.bind.DatatypeConverter

fun TransactionOuterClass.Transaction.toHash(): String {
    return DatatypeConverter.printHexBinary(Utils.hash(this)).toLowerCase()
}
