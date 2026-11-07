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

package jp.co.soramitsu.feature_main_api.domain.model

enum class PinCodeAction {
    CREATE_PIN_CODE,
    OPEN_PASSPHRASE,
    OPEN_SEED,
    OPEN_JSON,
    CHANGE_PIN_CODE,
    TIMEOUT_CHECK,
    LOGOUT,
    CUSTOM_NODE,
    SELECT_NODE,

    /**
     * Account options' "Backup to Google" and "Delete Google backup". The backup carries the
     * recovery phrase, raw seed and key-pair JSON, so it asks for the PIN like showing them does.
     * On success the PIN screen closes and hands [PIN_CONFIRMED_ACTION_KEY] back to the screen
     * that asked (MainRouter.returnFromPinConfirmation).
     */
    GOOGLE_BACKUP,
}

/**
 * Saved-state key under which the PIN screen hands the confirmed [PinCodeAction] name back to the
 * screen below it. The receiver removes it once handled, so a recreated screen never acts on the
 * same confirmation twice.
 */
const val PIN_CONFIRMED_ACTION_KEY = "PIN_CONFIRMED_ACTION"
