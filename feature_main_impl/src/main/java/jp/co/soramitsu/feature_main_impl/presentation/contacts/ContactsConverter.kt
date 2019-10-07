/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.contacts

import jp.co.soramitsu.contact_entry_list.list.models.ContactItem
import jp.co.soramitsu.feature_wallet_api.domain.model.Account

object ContactsConverter {

    fun fromVm(accountVms: List<Account>): List<ContactItem> {
        return accountVms.map {
            ContactItem(it.accountId, "${it.firstName} ${it.lastName}", "", "" + it.firstName[0] + it.lastName[0])
        }
    }
}
