/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R

enum class ResponseCode(val stringResource: Int) {

    OK(0),

    USER_NOT_FOUND(R.string.user_not_found),

    DID_NOT_FOUND(R.string.user_not_found),

    MNEMONIC_LENGTH_ERROR(R.string.mnemonic_length_error),

    MNEMONIC_IS_NOT_VALID(R.string.mnemonic_is_not_valid),

    USER_VALUES_NOT_FOUND(R.string.user_values_not_found),

    APPLICATION_FORM_NOT_FOUND(R.string.application_form_not_found),

    INVITATION_CODE_NOT_FOUND(R.string.invitation_code_not_found),

    NOT_ENOUGH_INVITATIONS(R.string.not_enough_invitations),

    PROJECT_NOT_FOUND(R.string.project_not_found),

    VOTING_NOT_ALLOWED(R.string.voting_is_not_allowed),

    VOTES_NOT_ENOUGH(R.string.votes_not_enough),

    UNAUTHORIZED(R.string.not_authorized),

    GENERAL_ERROR(R.string.general_error_message),

    CUSTOMER_NOT_FOUND(R.string.general_error_message),

    SMS_CODE_NOT_CORRECT(R.string.sms_code_not_correct),

    SMS_CODE_NOT_FOUND(R.string.sms_code_not_correct),

    SMS_CODE_EXPIRED(R.string.sms_code_expired),

    PHONE_ALREADY_EXIST(R.string.phone_already_exists),

    NETWORK_ERROR(R.string.network_error),

    INCORRECT_QUERY_PARAMS(R.string.search_string_error),

    USER_NOT_REGISTERED(R.string.user_not_found),

    IROHA_ERROR(R.string.general_error_message),

    UNSUPPORTED_QUERY(R.string.general_error_message),

    INVALID_QUERY_FORMAT(R.string.invalid_query_format),

    TOO_FREQUENT_REQUEST(0),

    SECTION_NOT_FOUND(0),

    INCORRECT_VOTES_VALUE_FORMAT(R.string.something_went_wrong),

    BROKEN_TRANSACTION(R.string.general_error_message),

    QR_ERROR(R.string.qr_error),

    QR_USER_NOT_FOUND(R.string.qr_user_not_found),

    PHONE_ALREADY_REGISTERED(R.string.phone_already_registered),

    PHONE_ALREADY_VERIFIED(R.string.phone_already_registered),

    SENDING_TO_MYSELF(R.string.sending_to_myself),

    FEE_RATE_NOT_AVAILABLE(R.string.fee_rate_not_available),

    WRONG_PIN_CODE(R.string.pincode_check_error);

    companion object {

        @JvmStatic fun contains(value: String): Boolean {
            return values().any { it.name == value }
        }
    }
}