/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import javax.inject.Inject

class PrefsUserDatasource @Inject constructor(
    private val prefsUtl: PrefsUtil
) : UserDatasource {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"
        private const val PREFS_PARENT_INVITATION = "parent_invitation"
        private const val PREFS_TOKENS = "prefs_tokens"
        private const val PREFS_INVITATIONS = "prefs_invitations"

        private const val KEY_USER_UD = "key_user_id"
        private const val KEY_FIRST_NAME = "key_first_name"
        private const val KEY_LAST_NAME = "key_last_name"
        private const val KEY_PHONE = "key_phone"
        private const val KEY_COUNTRY = "key_country"
        private const val KEY_INVITE_ACCEPT_MOMENT = "key_invite_accept_moment"
        private const val KEY_PARENT_ID = "key_parent_id"
        private const val KEY_STATUS = "key_status"
        private const val KEY_INVITE_CODE = "invite_code"
    }

    private val gson = Gson()

    override fun savePin(pin: String) {
        prefsUtl.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    override fun retrievePin(): String {
        return prefsUtl.getDecryptedString(PREFS_PIN_CODE)
    }

    override fun savePushToken(notificationToken: String) {
        prefsUtl.putEncryptedString(Const.DEVICE_TOKEN, notificationToken)
    }

    override fun retrievePushToken(): String {
        return prefsUtl.getDecryptedString(Const.DEVICE_TOKEN)
    }

    override fun saveIsPushTokenUpdateNeeded(updateNeeded: Boolean) {
        prefsUtl.putBoolean(Const.IS_PUSH_UPDATE_NEEDED, updateNeeded)
    }

    override fun isPushTokenUpdateNeeded(): Boolean {
        return prefsUtl.getBoolean(Const.IS_PUSH_UPDATE_NEEDED, false)
    }

    override fun saveUser(user: User) {
        prefsUtl.putString(KEY_USER_UD, user.id)
        prefsUtl.putString(KEY_FIRST_NAME, user.firstName)
        prefsUtl.putString(KEY_LAST_NAME, user.lastName)
        prefsUtl.putString(KEY_PARENT_ID, user.parentId)
        prefsUtl.putString(KEY_PHONE, user.phone)
        prefsUtl.putString(KEY_STATUS, user.status)
        prefsUtl.putString(KEY_COUNTRY, user.country)
        prefsUtl.putLong(KEY_INVITE_ACCEPT_MOMENT, user.inviteAcceptExpirationMomentMillis)
        prefsUtl.putString(KEY_USER_UD, user.values.userId)
        prefsUtl.putFloat(PREFS_TOKENS, user.values.tokens)
        prefsUtl.putInt(PREFS_INVITATIONS, user.values.invitations)
    }

    override fun retrieveUser(): User? {
        return if (prefsUtl.getString(KEY_USER_UD).isEmpty()) {
            null
        } else {
            User(
                prefsUtl.getString(KEY_USER_UD),
                prefsUtl.getString(KEY_FIRST_NAME),
                prefsUtl.getString(KEY_LAST_NAME),
                prefsUtl.getString(KEY_PHONE),
                prefsUtl.getString(KEY_STATUS),
                prefsUtl.getString(KEY_PARENT_ID),
                prefsUtl.getString(KEY_COUNTRY),
                prefsUtl.getLong(KEY_INVITE_ACCEPT_MOMENT, 0),
                retrieveUserValues()
            )
        }
    }

    private fun retrieveUserValues(): UserValues {
        return UserValues(
            prefsUtl.getInt(PREFS_INVITATIONS, 0),
            prefsUtl.getFloat(PREFS_TOKENS, 0f),
            prefsUtl.getString(KEY_USER_UD)
        )
    }

    override fun saveRegistrationState(onboardingState: OnboardingState) {
        prefsUtl.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    override fun retrieveRegistratrionState(): OnboardingState {
        val registrationStateString = prefsUtl.getString(PREFS_REGISTRATION_STATE)

        return if (registrationStateString.isEmpty()) {
            OnboardingState.INITIAL
        } else {
            OnboardingState.valueOf(registrationStateString)
        }
    }

    override fun clearUserData() {
        prefsUtl.clearAll()
    }

    override fun saveInvitationParent(parentInfo: InvitedUser) {
        prefsUtl.putEncryptedString(PREFS_PARENT_INVITATION, gson.toJson(parentInfo))
    }

    override fun retrieveInvitationParent(): InvitedUser? {
        val parentInfoString = prefsUtl.getDecryptedString(PREFS_PARENT_INVITATION)

        return if (parentInfoString.isEmpty()) {
            null
        } else {
            gson.fromJson(parentInfoString, InvitedUser::class.java)
        }
    }

    override fun saveUserReputation(reputationDto: Reputation) {
        prefsUtl.putInt(Const.USER_REPUTATION_RANK, reputationDto.rank)
        prefsUtl.putFloat(Const.USER_REPUTATION, reputationDto.reputation)
        prefsUtl.putInt(Const.USER_REPUTATION_TOTAL_RANK, reputationDto.totalRank)
    }

    override fun retrieveUserReputation(): Reputation {
        return Reputation(
            prefsUtl.getInt(Const.USER_REPUTATION_RANK, 0),
            prefsUtl.getFloat(Const.USER_REPUTATION, 0f),
            prefsUtl.getInt(Const.USER_REPUTATION_TOTAL_RANK, 0)
        )
    }

    override fun saveInvitedUsers(invitedUsers: Array<InvitedUser>) {
        prefsUtl.putString(Const.INVITED_USERS, gson.toJson(invitedUsers))
    }

    override fun retrieveInvitedUsers(): Array<InvitedUser>? {
        val invitedUsersJson = prefsUtl.getString(Const.INVITED_USERS)

        return if (invitedUsersJson.isEmpty()) {
            null
        } else {
            gson.fromJson<Array<InvitedUser>>(invitedUsersJson, object : TypeToken<Array<InvitedUser>>() {}.type)
        }
    }

    override fun saveParentInviteCode(inviteCode: String) {
        prefsUtl.putString(KEY_INVITE_CODE, inviteCode)
    }

    override fun getParentInviteCode(): String {
        return prefsUtl.getString(KEY_INVITE_CODE)
    }
}