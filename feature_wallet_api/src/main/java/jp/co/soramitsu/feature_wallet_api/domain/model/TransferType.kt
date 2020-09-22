/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_api.domain.model

enum class TransferType {
    XOR_TRANSFER,
    XOR_WITHDRAW,
    XORERC_TRANSFER,
    XORXORERC_TO_XOR,
    XORXORERC_TO_XORERC
}