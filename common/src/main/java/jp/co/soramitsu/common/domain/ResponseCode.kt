package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R

enum class ResponseCode(val stringResource: Int) {

    OK(0),

    USER_NOT_FOUND(R.string.common_error_user_not_found),

    DID_NOT_FOUND(R.string.common_error_user_not_found),

    MNEMONIC_LENGTH_ERROR(R.string.common_error_mnemonic_length_error),

    MNEMONIC_IS_NOT_VALID(R.string.common_error_mnemonic_is_not_valid),

    USER_VALUES_NOT_FOUND(R.string.common_error_user_values_not_found),

    INVITATION_CODE_NOT_FOUND(R.string.common_error_invitation_code_not_found),

    NOT_ENOUGH_INVITATIONS(R.string.common_error_not_enough_invitations),

    PROJECT_NOT_FOUND(R.string.common_error_project_not_found),

    VOTING_NOT_ALLOWED(R.string.common_error_voting_is_not_allowed),

    VOTES_NOT_ENOUGH(R.string.common_error_votes_not_enough),

    GENERAL_ERROR(R.string.common_error_general_message),

    CUSTOMER_NOT_FOUND(R.string.common_error_general_message),

    SMS_CODE_NOT_CORRECT(R.string.common_sms_code_not_correct),

    SMS_CODE_NOT_FOUND(R.string.common_sms_code_not_correct),

    SMS_CODE_EXPIRED(R.string.common_sms_code_expired),

    PHONE_ALREADY_EXIST(R.string.common_error_phone_already_exists),

    NETWORK_ERROR(R.string.common_error_network),

    INCORRECT_QUERY_PARAMS(R.string.common_error_search_string_error),

    USER_NOT_REGISTERED(R.string.common_error_user_not_found),

    IROHA_ERROR(R.string.common_error_general_message),

    UNSUPPORTED_QUERY(R.string.common_error_general_message),

    INVALID_QUERY_FORMAT(R.string.common_error_invalid_query_format),

    TOO_FREQUENT_REQUEST(R.string.phone_verification_too_frequent_message),

    SECTION_NOT_FOUND(0),

    INCORRECT_VOTES_VALUE_FORMAT(R.string.common_error_votes_invalid_format),

    BROKEN_TRANSACTION(R.string.common_error_general_message),

    PHONE_ALREADY_REGISTERED(R.string.common_error_phone_already_registered),

    PHONE_ALREADY_VERIFIED(R.string.common_error_phone_already_registered),

    FEE_RATE_NOT_AVAILABLE(R.string.common_error_fee_rate_not_available),

    WRONG_PIN_CODE(R.string.common_error_pincode_check_error),

    AMBIGUOUS_RESULT(R.string.common_error_ambiguous_result),

    WRONG_USER_STATUS(R.string.common_error_wrong_user_status),

    INVITER_REGISTERED_AFTER_INVITEE(R.string.common_error_inviter_registered_after_invitee),

    INVITATION_ACCEPTING_WINDOW_CLOSED(R.string.common_error_invitation_accepting_window_closed),

    PARENT_ALREADY_EXISTS(R.string.common_error_invitation_already_accepted),

    SELF_INVITATION(R.string.common_error_self_invitation),

    BOUND_ETH_ADDRESS_NOT_FOUND(0);

    companion object {

        @JvmStatic fun contains(value: String): Boolean {
            return values().any { it.name == value }
        }
    }
}
