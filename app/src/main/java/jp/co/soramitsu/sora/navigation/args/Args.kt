/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.navigation.args

import android.os.Bundle

fun withArgs(argsBuilder: Bundle.() -> Unit) = Bundle().apply(argsBuilder)
