/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.domain

import android.net.InetAddresses
import javax.inject.Inject

class InetAddressesWrapper @Inject constructor() {

    fun isIpAddressValid(address: String) = InetAddresses.isNumericAddress(address)
}
