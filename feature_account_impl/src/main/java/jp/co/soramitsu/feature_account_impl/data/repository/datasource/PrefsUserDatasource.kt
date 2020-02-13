/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import javax.inject.Inject

class PrefsUserDatasource @Inject constructor(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val serializer: Serializer
) : UserDatasource {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"
        private const val PREFS_PARENT_INVITATION = "parent_invitation"

        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_FIRST_NAME = "key_first_name"
        private const val KEY_LAST_NAME = "key_last_name"
        private const val KEY_PHONE = "key_phone"
        private const val KEY_COUNTRY = "key_country"
        private const val KEY_INVITE_ACCEPT_MOMENT = "key_invite_accept_moment"
        private const val KEY_PARENT_ID = "key_parent_id"
        private const val KEY_STATUS = "key_status"
        private const val KEY_PARENT_INVITE_CODE = "invite_code"
        private const val KEY_USER_INVITE_CODE = "user_invite_code"
    }

    override fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    override fun retrievePin(): String {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }

    override fun savePushToken(notificationToken: String) {
        encryptedPreferences.putEncryptedString(Const.DEVICE_TOKEN, notificationToken)
    }

    override fun retrievePushToken(): String {
        return encryptedPreferences.getDecryptedString(Const.DEVICE_TOKEN)
    }

    override fun saveIsPushTokenUpdateNeeded(updateNeeded: Boolean) {
        preferences.putBoolean(Const.IS_PUSH_UPDATE_NEEDED, updateNeeded)
    }

    override fun isPushTokenUpdateNeeded(): Boolean {
        return preferences.getBoolean(Const.IS_PUSH_UPDATE_NEEDED, false)
    }

    override fun saveUser(user: User) {
        preferences.putString(KEY_USER_ID, user.id)
        preferences.putString(KEY_FIRST_NAME, user.firstName)
        preferences.putString(KEY_LAST_NAME, user.lastName)
        preferences.putString(KEY_PARENT_ID, user.parentId)
        preferences.putString(KEY_PHONE, user.phone)
        preferences.putString(KEY_STATUS, user.status)
        preferences.putString(KEY_COUNTRY, user.country)
        preferences.putLong(KEY_INVITE_ACCEPT_MOMENT, user.inviteAcceptExpirationMomentMillis)
        preferences.putString(KEY_USER_ID, user.values.userId)
        preferences.putString(KEY_USER_INVITE_CODE, user.values.invitationCode)
    }

    override fun retrieveUser(): User? {
        return if (preferences.getString(KEY_USER_ID).isEmpty()) {
            null
        } else {
            User(
                preferences.getString(KEY_USER_ID),
                preferences.getString(KEY_FIRST_NAME),
                preferences.getString(KEY_LAST_NAME),
                preferences.getString(KEY_PHONE),
                preferences.getString(KEY_STATUS),
                preferences.getString(KEY_PARENT_ID),
                preferences.getString(KEY_COUNTRY),
                preferences.getLong(KEY_INVITE_ACCEPT_MOMENT, 0),
                retrieveUserValues()
            )
        }
    }

    private fun retrieveUserValues(): UserValues {
        return UserValues(
            preferences.getString(KEY_USER_INVITE_CODE),
            preferences.getString(KEY_USER_ID)
        )
    }

    override fun saveRegistrationState(onboardingState: OnboardingState) {
        preferences.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    override fun retrieveRegistratrionState(): OnboardingState {
        val registrationStateString = preferences.getString(PREFS_REGISTRATION_STATE)
        return if (registrationStateString.isEmpty()) {
            OnboardingState.INITIAL
        } else {
            OnboardingState.valueOf(registrationStateString)
        }
    }

    override fun clearUserData() {
        preferences.clearAll()
    }

    override fun saveInvitationParent(parentInfo: InvitedUser) {
        encryptedPreferences.putEncryptedString(PREFS_PARENT_INVITATION, serializer.serialize(parentInfo))
    }

    override fun retrieveInvitationParent(): InvitedUser? {
        val parentInfoString = encryptedPreferences.getDecryptedString(PREFS_PARENT_INVITATION)
        return if (parentInfoString.isEmpty()) {
            null
        } else {
            serializer.deserialize(parentInfoString, InvitedUser::class.java)
        }
    }

    override fun saveUserReputation(reputationDto: Reputation) {
        preferences.putInt(Const.USER_REPUTATION_RANK, reputationDto.rank)
        preferences.putFloat(Const.USER_REPUTATION, reputationDto.reputation)
        preferences.putInt(Const.USER_REPUTATION_TOTAL_RANK, reputationDto.totalRank)
    }

    override fun retrieveUserReputation(): Reputation {
        return Reputation(
            preferences.getInt(Const.USER_REPUTATION_RANK, 0),
            preferences.getFloat(Const.USER_REPUTATION, 0f),
            preferences.getInt(Const.USER_REPUTATION_TOTAL_RANK, 0)
        )
    }

    override fun saveInvitedUsers(invitedUsers: Array<InvitedUser>) {
        preferences.putString(Const.INVITED_USERS, serializer.serialize(invitedUsers))
    }

    override fun retrieveInvitedUsers(): Array<InvitedUser>? {
        val invitedUsersJson = preferences.getString(Const.INVITED_USERS)

        return if (invitedUsersJson.isEmpty()) {
            null
        } else {
            serializer.deserialize<Array<InvitedUser>>(invitedUsersJson, object : TypeToken<Array<InvitedUser>>() {}.type)
        }
    }

    override fun saveParentInviteCode(inviteCode: String) {
        preferences.putString(KEY_PARENT_INVITE_CODE, inviteCode)
    }

    override fun getParentInviteCode(): String {
        return preferences.getString(KEY_PARENT_INVITE_CODE)
    }

    override fun getCurrentLanguage(): String {
        return preferences.getCurrentLanguage()
    }

    override fun changeLanguage(language: String) {
        preferences.saveCurrentLanguage(language)
    }
}