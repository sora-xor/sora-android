/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.base

import android.app.ProgressDialog
import android.content.Context
import jp.co.soramitsu.common.R

class SoraProgressDialog(context: Context) : ProgressDialog(context) {

    init {
        setMessage(context.getString(R.string.loading))
        setCancelable(false)
    }
}