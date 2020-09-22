/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

class Const private constructor() {

    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        const val SORA_TERMS_PAGE = "https://sora.org/terms"
        const val SORA_PRIVACY_PAGE = "https://sora.org/privacy"
        const val XOR_ASSET_ID = "xor#sora"

        const val PIN_CODE_ACTION = "pin_code_action"
        const val IS_PUSH_UPDATE_NEEDED = "is_push_update_needed"
        const val DEVICE_TOKEN = "device_token"

        const val INVITED_USERS = "prefs_invited_users"

        // User reputation
        const val USER_REPUTATION = "prefs_user_reputation"
        const val USER_REPUTATION_RANK = "prefs_user_reputation_rank"
        const val USER_REPUTATION_TOTAL_RANK = "prefs_user_reputation_total_rank"

        const val NAME_MAX_LENGTH = 30

        const val NO_ICON_RESOURCE = 0

        val SORA_SYMBOL: CharSequence = "\ue000"
        val PROJECT_DID = arrayOf("did:sora:passport")
    }
}