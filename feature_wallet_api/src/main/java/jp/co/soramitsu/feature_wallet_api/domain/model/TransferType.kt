/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

enum class TransferType {
    VAL_TRANSFER,
    VAL_WITHDRAW,
    VALERC_TRANSFER,
    VALVALERC_TO_VAL,
    VALVALERC_TO_VALERC
}