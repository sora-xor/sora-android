/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory

interface TransactionMappers {

    fun mapTransaction(tx: Transaction, curAddress: String): EventUiModel.EventTxUiModel
}
