/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

data class AccountListScreenState(val chooserActivated: Boolean = false, val accountList: List<ExportAccountData> = emptyList())
