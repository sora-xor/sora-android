/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.core_db.model.SoraCardInfoLocal

object SoraCardInfoMapper {

    fun map(infoLocal: SoraCardInfoLocal): SoraCardInformation {
        return SoraCardInformation(
            id = infoLocal.id,
            accessToken = infoLocal.accessToken,
            accessTokenExpirationTime = infoLocal.accessTokenExpirationTime,
            refreshToken = infoLocal.refreshToken,
            kycStatus = infoLocal.kycStatus
        )
    }
}
