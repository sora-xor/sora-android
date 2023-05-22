/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import javax.inject.Inject
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager

class InvitationInteractor @Inject constructor(
    private val soraConfigManager: SoraConfigManager,
) {

    suspend fun getInviteLink(): String = soraConfigManager.getInviteLink()
}
