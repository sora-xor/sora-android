/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_api.domain.model

enum class PinCodeAction {
    CREATE_PIN_CODE,
    OPEN_PASSPHRASE,
    OPEN_SEED,
    OPEN_JSON,
    CHANGE_PIN_CODE,
    TIMEOUT_CHECK,
    LOGOUT,
    CUSTOM_NODE,
    SELECT_NODE
}
