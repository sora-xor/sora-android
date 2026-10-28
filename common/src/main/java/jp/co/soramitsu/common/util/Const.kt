/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

open class ParseModel

object Const {

    const val SORA = "SORA"
    const val HISTORY_PAGE_SIZE = 80
    const val WEBSITE = "https://sora.org/"
    const val PARLIAMENT_LEARN_MORE_LINK =
        "https://medium.com/sora-xor/the-sora-parliament-af8184dae384"
    const val SOURCE_LINK = "https://github.com/sora-xor/Sora-Android"
    const val STAKING_LEARN_MORE_LINK =
        "https://medium.com/sora-xor/sora-validator-reward-token-val-c96a8afb8541"
    const val POLKASWAP_WEBSITE = "https://polkaswap.io"
    const val POLKASWAP_FAQ = "https://wiki.sora.org/polkaswap/polkaswap-faq"
    const val POLKASWAP_PRIVACY_POLICY = "https://wiki.sora.org/polkaswap/privacy"
    const val POLKASWAP_MEMORANDUM = "https://wiki.sora.org/polkaswap/terms"
    const val TELEGRAM_LINK = "https://t.me/sora_xor"
    const val SORA_TERMS_PAGE = "https://sora.org/terms"
    const val SORA_FAQ_PAGE = "https://wiki.sora.org/sora-faq"
    const val SORA_PRIVACY_PAGE = "https://sora.org/privacy"
    const val HOW_TO_RUN_A_NODE_PAGE = "https://medium.com/sora-xor/how-to-run-a-sora-testnet-node-a4d42a9de1af"
    const val XOR_ASSET_ID = "xor#sora"
    const val VAL_ASSET_ID = "val#sora"

    const val PIN_CODE_ACTION = "pin_code_action"
    const val PIN_ADDRESS = "pin_code_address"
    const val IS_PUSH_UPDATE_NEEDED = "is_push_update_needed"
    const val DEVICE_TOKEN = "device_token"

    const val INVITED_USERS = "prefs_invited_users"

    const val NAME_MAX_LENGTH = 25

    const val NO_ICON_RESOURCE = 0

    val PROJECT_DID = arrayOf("did:sora:passport")

    val NOTARY_ADDRESS = "notary@notary"
}
