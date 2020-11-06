package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class PrefsUserDatasourceTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var encryptedPreferences: EncryptedPreferences
    @Mock private lateinit var serializer: Serializer

    private lateinit var prefsUserDatasource: PrefsUserDatasource
    private val expectedJson = "{}"

    @Before fun setUp() {
        prefsUserDatasource = PrefsUserDatasource(preferences, encryptedPreferences, serializer)
    }

    @Test fun `save pin calls prefsutil putEncryptedString for PREFS_PIN_CODE`() {
        val pin = "1234"
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.savePin(pin)

        verify(encryptedPreferences).putEncryptedString(pincodeKey, pin)
    }

    @Test fun `retrieve pin calls prefsutil getDecryptedString for PREFS_PIN_CODE`() {
        val pincodeKey = "user_pin_code"

        prefsUserDatasource.retrievePin()

        verify(encryptedPreferences).getDecryptedString(pincodeKey)
    }

    @Test fun `save User called`() {
        val keyUserId = "key_user_id"
        val keyFirstName = "key_first_name"
        val keyLastName = "key_last_name"
        val keyPhone = "key_phone"
        val keyCountry = "key_country"
        val keyInviteAcceptMoment = "key_invite_accept_moment"
        val keyParentId = "key_parent_id"
        val keyStatus = "key_status"
        val keyTokens = "prefs_tokens"
        val keyInvitations = "prefs_invitations"

        val user = User(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100,
            UserValues(
                "invitationCode",
                "id"
            )
        )

        prefsUserDatasource.saveUser(user)

        verify(preferences, times(2)).putString(keyUserId, user.id)
        verify(preferences).putString(keyFirstName, user.firstName)
        verify(preferences).putString(keyLastName, user.lastName)
        verify(preferences).putString(keyPhone, user.phone)
        verify(preferences).putString(keyStatus, user.status)
        verify(preferences).putString(keyParentId, user.parentId)
        verify(preferences).putString(keyCountry, user.country)
        verify(preferences).putLong(keyInviteAcceptMoment, user.inviteAcceptExpirationMomentMillis)
    }

    @Test fun `retrieve user if userId is null`() {
        val keyUserId = "key_user_id"
        given(preferences.getString(keyUserId)).willReturn("")

        assertNull(prefsUserDatasource.retrieveUser())
    }

    @Test fun `retrieve user called`() {
        val keyUserId = "key_user_id"
        val keyFirstName = "key_first_name"
        val keyLastName = "key_last_name"
        val keyPhone = "key_phone"
        val keyCountry = "key_country"
        val keyInviteAcceptMoment = "key_invite_accept_moment"
        val keyParentId = "key_parent_id"
        val keyStatus = "key_status"
        val keyUserInviteCode = "user_invite_code"

        val user = User(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100,
            UserValues(
                "invitationCode",
                "id"
            )
        )
        given(preferences.getString(keyUserId)).willReturn(user.id)
        given(preferences.getString(keyFirstName)).willReturn(user.firstName)
        given(preferences.getString(keyLastName)).willReturn(user.lastName)
        given(preferences.getString(keyPhone)).willReturn(user.phone)
        given(preferences.getString(keyCountry)).willReturn(user.country)
        given(preferences.getString(keyStatus)).willReturn(user.status)
        given(preferences.getLong(keyInviteAcceptMoment, 0)).willReturn(user.inviteAcceptExpirationMomentMillis)
        given(preferences.getString(keyParentId)).willReturn(user.parentId)
        given(preferences.getString(keyUserInviteCode)).willReturn(user.values.invitationCode)

        assertEquals(user, prefsUserDatasource.retrieveUser())
    }

    @Test fun `save registration state is called`() {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.INITIAL

        prefsUserDatasource.saveRegistrationState(onboardingState)

        verify(preferences).putString(keyRegistrationState, onboardingState.toString())
    }

    @Test fun `retrieve registration state called`() {
        val keyRegistrationState = "registration_state"
        val onboardingState = OnboardingState.PHONE_NUMBER_CONFIRMED
        given(preferences.getString(keyRegistrationState)).willReturn(onboardingState.toString())

        assertEquals(onboardingState, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test fun `retrieve registration state if empty`() {
        val keyRegistrationState = "registration_state"
        given(preferences.getString(keyRegistrationState)).willReturn("")

        assertEquals(OnboardingState.INITIAL, prefsUserDatasource.retrieveRegistratrionState())
    }

    @Test fun `clear user data called`() {
        prefsUserDatasource.clearUserData()

        verify(preferences).clearAll()
    }

    @Test fun `save invitation parent called`() {
        val keyParentInvitation = "parent_invitation"
        val invitedUser = InvitedUser("firstName", "lastName")
        given(serializer.serialize(invitedUser)).willReturn(expectedJson)

        prefsUserDatasource.saveInvitationParent(invitedUser)

        verify(encryptedPreferences).putEncryptedString(keyParentInvitation, expectedJson)
    }

    @Test fun `retrieve invitation parent called if empty`() {
        val keyParentInvitation = "parent_invitation"
        given(encryptedPreferences.getDecryptedString(keyParentInvitation)).willReturn("")

        assertNull(prefsUserDatasource.retrieveInvitationParent())
    }

    @Test fun `retrieve invitation parent called`() {
        val keyParentInvitation = "parent_invitation"
        val invitedUser = InvitedUser("firstName", "lastName")
        given(serializer.deserialize(expectedJson, InvitedUser::class.java)).willReturn(invitedUser)
        given(encryptedPreferences.getDecryptedString(keyParentInvitation)).willReturn(expectedJson)

        assertEquals(invitedUser, prefsUserDatasource.retrieveInvitationParent())
    }

    @Test fun `save user reputation called`() {
        val keyUserReputation = "prefs_user_reputation"
        val keyUserReputationRank = "prefs_user_reputation_rank"
        val keyUserReputationRankTotal = "prefs_user_reputation_total_rank"
        val reputationDto = Reputation(
            3,
            4f,
            5
        )

        prefsUserDatasource.saveUserReputation(reputationDto)

        verify(preferences).putFloat(keyUserReputation, reputationDto.reputation)
        verify(preferences).putInt(keyUserReputationRank, reputationDto.rank)
        verify(preferences).putInt(keyUserReputationRankTotal, reputationDto.totalRank)
    }

    @Test fun `retrieve user reputation called`() {
        val keyUserReputation = "prefs_user_reputation"
        val keyUserReputationRank = "prefs_user_reputation_rank"
        val keyUserReputationRankTotal = "prefs_user_reputation_total_rank"
        val reputationDto = Reputation(
            3,
            4f,
            5
        )

        given(preferences.getFloat(keyUserReputation, 0f)).willReturn(reputationDto.reputation)
        given(preferences.getInt(keyUserReputationRank, 0)).willReturn(reputationDto.rank)
        given(preferences.getInt(keyUserReputationRankTotal, 0)).willReturn(reputationDto.totalRank)

        assertEquals(reputationDto, prefsUserDatasource.retrieveUserReputation())
    }

    @Test fun `save invited users called`() {
        val keyInvitedUsers = "prefs_invited_users"
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName"))
        given(serializer.serialize(invitedUsers)).willReturn(expectedJson)

        prefsUserDatasource.saveInvitedUsers(invitedUsers)

        verify(preferences).putString(keyInvitedUsers, expectedJson)
    }

    @Test fun `retrieve invited users called if empty`() {
        val keyInvitedUsers = "prefs_invited_users"
        given(preferences.getString(keyInvitedUsers)).willReturn("")

        assertNull(prefsUserDatasource.retrieveInvitedUsers())
    }

    @Test fun `retrieve invited users called`() {
        val keyInvitedUsers = "prefs_invited_users"
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName"))
        given(preferences.getString(keyInvitedUsers)).willReturn(expectedJson)
        given(serializer.deserialize<Array<InvitedUser>>(expectedJson, object : TypeToken<Array<InvitedUser>>() {}.type)).willReturn(invitedUsers)

        assertEquals(invitedUsers, prefsUserDatasource.retrieveInvitedUsers())
    }

    @Test fun `save parent invite code called`() {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        prefsUserDatasource.saveParentInviteCode(inviteCode)

        verify(preferences).putString(keyInviteCode, inviteCode)
    }

    @Test fun `retrieve parent invite code called`() {
        val inviteCode = "1234"
        val keyInviteCode = "invite_code"
        given(preferences.getString(keyInviteCode)).willReturn(inviteCode)

        assertEquals(inviteCode, prefsUserDatasource.getParentInviteCode())
    }

    @Test fun `get current language called`() {
        val language = "ru"
        given(preferences.getCurrentLanguage()).willReturn(language)

        assertEquals(language, prefsUserDatasource.getCurrentLanguage())
    }

    @Test fun `save current language called`() {
        val language = "ru"

        prefsUserDatasource.changeLanguage(language)

        verify(preferences).saveCurrentLanguage(language)
    }
}