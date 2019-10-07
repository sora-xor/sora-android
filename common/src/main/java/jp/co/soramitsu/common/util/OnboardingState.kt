/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

enum class OnboardingState {
    INITIAL,
    PHONE_NUMBER_CONFIRMED,
    REGISTRATION_FINISHED,
    @Deprecated("") PERSONAL_DATA_ENTERED,
    @Deprecated("") SMS_REQUESTED,
    @Deprecated("") PIN_CODE_SET
}