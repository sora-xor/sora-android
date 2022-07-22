/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

data class NeedsMigrationResponse(val asset_id: String, val symbol: String, val precision: String)
