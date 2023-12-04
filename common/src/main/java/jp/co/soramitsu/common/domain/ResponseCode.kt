/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.R

enum class ResponseCode(val messageResource: Int, val titleResource: Int = R.string.common_error_general_title) {

    OK(0),

    USER_NOT_FOUND(R.string.common_error_user_not_found),

    DID_NOT_FOUND(R.string.common_error_user_not_found),

    MNEMONIC_LENGTH_ERROR(R.string.common_error_mnemonic_length_error),

    MNEMONIC_IS_NOT_VALID(R.string.common_error_mnemonic_is_not_valid, R.string.mnemonic_invalid_title),

    RAW_SEED_IS_NOT_VALID(R.string.common_error_seed_is_not_valid, R.string.common_error_seed_is_not_valid_title),

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

    BOUND_ETH_ADDRESS_NOT_FOUND(0),

    ACCOUNT_ALREADY_IMPORTED(R.string.account_already_imported_description, R.string.account_already_imported),

    GOOGLE_LOGIN_FAILED(R.string.common_google_authorization_error),

    GOOGLE_BACKUP_DECRYPTION_FAILED(R.string.common_google_backup_decryption_failed_2),

    NOW_BROWSER_FOUND(R.string.common_error_no_browser_error);

    companion object {

        @JvmStatic
        fun contains(value: String): Boolean {
            return values().any { it.name == value }
        }
    }
}
