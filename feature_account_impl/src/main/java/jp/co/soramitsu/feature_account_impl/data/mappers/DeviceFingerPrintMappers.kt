/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.DeviceFingerPrint
import jp.co.soramitsu.feature_account_impl.data.network.model.DeviceFingerPrintRemote

fun mapDeviceFingerPrintToFingerPrintRemote(deviceFingerPrint: DeviceFingerPrint): DeviceFingerPrintRemote {
    return with(deviceFingerPrint) {
        DeviceFingerPrintRemote(model, osVersion, screenWidth, screenHeight, language, country, timezone)
    }
}