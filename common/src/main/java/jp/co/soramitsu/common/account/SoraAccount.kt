/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.account

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SoraAccount(
    val substrateAddress: String,
    val accountName: String,
) : Parcelable
