/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.core_db.model.SoraAccountLocal

object SoraAccountMapper {

    fun map(account: SoraAccountLocal): SoraAccount {
        return SoraAccount(
            substrateAddress = account.substrateAddress,
            accountName = account.accountName,
        )
    }

    fun map(account: SoraAccount): SoraAccountLocal {
        return SoraAccountLocal(
            substrateAddress = account.substrateAddress,
            accountName = account.accountName,
        )
    }
}
