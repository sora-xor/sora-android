/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.JsonObject
import io.reactivex.Single
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.Const.Companion.PROJECT_DID
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.ActivityFeedDao
import jp.co.soramitsu.core_db.dao.AnnouncementDao
import jp.co.soramitsu.core_db.dao.GalleryDao
import jp.co.soramitsu.core_db.dao.ProjectDao
import jp.co.soramitsu.core_db.dao.ProjectDetailsDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.dao.VotesHistoryDao
import jp.co.soramitsu.core_db.model.ActivityFeedLocal
import jp.co.soramitsu.core_db.model.AnnouncementLocal
import jp.co.soramitsu.core_network_api.data.dto.StatusDto
import jp.co.soramitsu.core_network_api.data.response.BaseResponse
import jp.co.soramitsu.core_network_api.domain.model.AppLinksProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserCreatingCase
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.data.mappers.ActivityGsonConverter
import jp.co.soramitsu.feature_account_impl.data.network.AccountNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.ActivityFeedNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.NotificationNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.model.AnnouncementRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.DeviceFingerPrintRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.InvitedRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.ReputationRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.UserRemote
import jp.co.soramitsu.feature_account_impl.data.network.model.UserValuesRemote
import jp.co.soramitsu.feature_account_impl.data.network.request.CreateUserRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.RegistrationRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.SaveUserDataRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.TokenChangeRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.VerifyCodeRequest
import jp.co.soramitsu.feature_account_impl.data.network.response.AnnouncementResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.CheckInviteCodeAvailableResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.CheckVersionSupportResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetActivityFeedResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetReputationResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.GetUserResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.InvitedUsersResponse
import jp.co.soramitsu.feature_account_impl.data.network.response.SendSMSResponse
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(MockitoJUnitRunner::class)
class UserRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var userDatasource: UserDatasource
    @Mock private lateinit var accountNetworkApi: AccountNetworkApi
    @Mock private lateinit var notificationNetworkApi: NotificationNetworkApi
    @Mock private lateinit var activityFeedNetworkApi: ActivityFeedNetworkApi
    @Mock private lateinit var appVersionProvider: AppVersionProvider
    @Mock private lateinit var activityGsonConverter: ActivityGsonConverter
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var activityFeedDao: ActivityFeedDao
    @Mock private lateinit var announcementDao: AnnouncementDao
    @Mock private lateinit var projectDao: ProjectDao
    @Mock private lateinit var galleryDao: GalleryDao
    @Mock private lateinit var projectDetailsDao: ProjectDetailsDao
    @Mock private lateinit var votesHistoryDao: VotesHistoryDao
    @Mock private lateinit var transactionDao: TransactionDao
    @Mock private lateinit var appLinkProvider: AppLinksProvider
    @Mock private lateinit var deviceParamsProvider: DeviceParamsProvider
    @Mock private lateinit var languagesHolder: LanguagesHolder

    private lateinit var userRepository: UserRepositoryImpl

    @Before fun setUp() {
        userRepository = UserRepositoryImpl(
            userDatasource,
            accountNetworkApi,
            notificationNetworkApi,
            activityFeedNetworkApi,
            appVersionProvider,
            activityGsonConverter,
            db,
            appLinkProvider,
            deviceParamsProvider,
            languagesHolder
        )
    }

    @Test fun `get AppVersion called`() {
        val version = "1.0"
        given(appVersionProvider.getVersionName()).willReturn(version)

        userRepository.getAppVersion()
            .test()
            .assertResult(version)

        verify(appVersionProvider).getVersionName()
    }

    @Test fun `save pin called`() {
        val pin = "1234"

        userRepository.savePin(pin)

        verify(userDatasource).savePin(pin)
    }

    @Test fun `retrieve pin called`() {
        val pin = "1234"
        given(userDatasource.retrievePin()).willReturn(pin)

        assertEquals(pin, userRepository.retrievePin())
    }

    @Test fun `update push token if needed called with updateNeeded false`() {
        val isPushTokenUpdateNeeded = false
        given(userDatasource.isPushTokenUpdateNeeded()).willReturn(isPushTokenUpdateNeeded)

        userRepository.updatePushTokenIfNeeded()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userDatasource, times(0)).retrievePushToken()
    }

    @Test fun `update push token if needed called`() {
        val isPushTokenUpdateNeeded = true
        val token = "token"
        val tokenChangeRequest = TokenChangeRequest(token, null)
        given(userDatasource.isPushTokenUpdateNeeded()).willReturn(isPushTokenUpdateNeeded)
        given(userDatasource.retrievePushToken()).willReturn(token)
        given(notificationNetworkApi.changeToken(tokenChangeRequest)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))
        given(notificationNetworkApi.setPermissions(PROJECT_DID)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.updatePushTokenIfNeeded()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userDatasource).isPushTokenUpdateNeeded()
        verify(userDatasource).retrievePushToken()
        verify(notificationNetworkApi).changeToken(tokenChangeRequest)
        verify(notificationNetworkApi).setPermissions(PROJECT_DID)
    }

    @Test fun `get invited users called update cached false`() {
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName"))

        given(userDatasource.retrieveInvitedUsers()).willReturn(invitedUsers)
        given(userDatasource.retrieveInvitationParent()).willReturn(invitedUsers[0])

        userRepository.getInvitedUsers(false)
            .test()
            .assertResult(Invitations(invitedUsers.toList(), invitedUsers[0]))
    }

    @Test fun `get invited users called update cached false and invitedUsersLocal is null`() {
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName"))
        val invitedRemote = arrayOf(InvitedRemote("firstName", "lastName"))
        given(accountNetworkApi.getInvitedUsers()).willReturn(Single.just(InvitedUsersResponse(invitedRemote, StatusDto("Ok", ""), invitedRemote[0])))

        userRepository.getInvitedUsers(false)
            .test()
            .assertResult(Invitations(invitedUsers.toList(), invitedUsers[0]))

        verify(userDatasource).retrieveInvitedUsers()
        verify(userDatasource).saveInvitedUsers(invitedUsers)
        verify(userDatasource).saveInvitationParent(invitedUsers[0])
    }

    @Test fun `get invited users called update cached true`() {
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName"))
        val invitedRemote = arrayOf(InvitedRemote("firstName", "lastName"))
        given(accountNetworkApi.getInvitedUsers()).willReturn(Single.just(InvitedUsersResponse(invitedRemote, StatusDto("Ok", ""), invitedRemote[0])))

        userRepository.getInvitedUsers(true)
            .test()
            .assertResult(Invitations(invitedUsers.toList(), invitedUsers[0]))

        verify(userDatasource, times(0)).retrieveInvitedUsers()
        verify(userDatasource).saveInvitedUsers(invitedUsers)
        verify(userDatasource).saveInvitationParent(invitedUsers[0])
    }

    @Test fun `get users called update cached false`() {
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
        given(userDatasource.retrieveUser()).willReturn(user)

        userRepository.getUser(false)
            .test()
            .assertResult(user)
    }

    @Test fun `get users called update cached false and user is null`() {
        val user = User(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100000,
            UserValues(
                "invitationCode",
                "id"
            )
        )
        val userRemote = UserRemote(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100,
            UserValuesRemote(
                "id",
                "invitationCode"
            )
        )
        given(accountNetworkApi.getUser()).willReturn(Single.just(GetUserResponse(userRemote, StatusDto("Ok", ""))))

        userRepository.getUser(false)
            .test()
            .assertResult(user)

        verify(userDatasource).saveUser(user)
    }

    @Test fun `get users called update cached true`() {
        val user = User(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100000,
            UserValues(
                "invitationCode",
                "id"
            )
        )
        val userRemote = UserRemote(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100,
            UserValuesRemote(
                "id",
                "invitationCode"
            )
        )
        given(accountNetworkApi.getUser()).willReturn(Single.just(GetUserResponse(userRemote, StatusDto("Ok", ""))))

        userRepository.getUser(true)
            .test()
            .assertResult(user)

        verify(userDatasource).saveUser(user)
    }

    @Test fun `get invitation link called`() {
        val invitationLink = "https://link.link/"
        val user = User(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100000,
            UserValues(
                "invitationCode",
                "id"
            )
        )
        val userRemote = UserRemote(
            "id",
            "firstName",
            "lastName",
            "1234",
            "registered",
            "parentid",
            "country",
            100,
            UserValuesRemote(
                "id",
                "invitationCode"
            )
        )
        given(accountNetworkApi.getUser()).willReturn(Single.just(GetUserResponse(userRemote, StatusDto("Ok", ""))))

        given(appLinkProvider.inviteUrl).willReturn(invitationLink)
        val expectedResult = "$invitationLink${user.values.invitationCode}"

        userRepository.getInvitationLink()
            .test()
            .assertResult(expectedResult)
    }

    @Test fun `get user reputation called with updateCached false`() {
        val updateCached = false
        val reputation = Reputation(1, 2f, 3)
        given(userDatasource.retrieveUserReputation()).willReturn(reputation)

        userRepository.getUserReputation(updateCached)
            .test()
            .assertResult(reputation)
    }

    @Test fun `get user reputation called with updateCached true`() {
        val updateCached = true
        val reputationRemote = ReputationRemote(1, 2f, 3)
        val reputation = Reputation(1, 2f, 3)
        given(accountNetworkApi.getUserReputation()).willReturn(Single.just(GetReputationResponse(reputationRemote, StatusDto("Ok", ""))))

        userRepository.getUserReputation(updateCached)
            .test()
            .assertResult(reputation)

        verify(userDatasource).saveUserReputation(reputation)
    }

    @Test fun `get activity feed called updateCached false`() {
        val activityFeedLocalList = mutableListOf(ActivityFeedLocal(1, "type", "title", "description", "voteString", 100, R.drawable.icon_activity_invite, -1))
        val activityFeedList = mutableListOf(ActivityFeed("type", "title", "description", "voteString", Date(100), R.drawable.icon_activity_invite))

        given(db.activityFeedDao()).willReturn(activityFeedDao)
        given(activityFeedDao.getActivityFeedList()).willReturn(Single.just(activityFeedLocalList))

        userRepository.getActivityFeed(3, 3, false)
            .test()
            .assertResult(activityFeedList)
    }

    @Test fun `get activity feed called updateCached true`() {
        val user = User(
            "1",
            "firstName",
            "lastName",
            "1234",
            "status",
            "parentid",
            "country",
            100,
            UserValues(
                "invitationCode",
                "1"
            )
        )
        val jsonTemplate = mutableListOf(JsonObject())
        val activityFeedList = mutableListOf(ActivityFeed("type", "title", "description", "voteString", Date(100), R.drawable.icon_activity_invite))
        given(activityFeedNetworkApi.getActivityFeed(3, 3)).willReturn(Single.just(GetActivityFeedResponse(jsonTemplate, jsonTemplate[0], jsonTemplate[0], StatusDto("Ok", ""))))
        given(userDatasource.retrieveUser()).willReturn(user)
        given(activityGsonConverter.convertActivityItems(jsonTemplate, jsonTemplate[0], jsonTemplate[0], user.id)).willReturn(activityFeedList)

        userRepository.getActivityFeed(3, 3, true)
            .test()
            .assertResult(activityFeedList)

        verify(db, times(0)).runInTransaction(any())
    }

    @Test fun `get activity feed called updateCached true with offset 0`() {
        val user = User(
            "1",
            "firstName",
            "lastName",
            "1234",
            "status",
            "parentid",
            "country",
            100,
            UserValues(
                "invitationCode",
                "1"
            )
        )
        val jsonTemplate = mutableListOf(JsonObject())
        val activityFeedList = mutableListOf(ActivityFeed("type", "title", "description", "voteString", Date(100), R.drawable.icon_activity_invite))
        given(activityFeedNetworkApi.getActivityFeed(3, 0)).willReturn(Single.just(GetActivityFeedResponse(jsonTemplate, jsonTemplate[0], jsonTemplate[0], StatusDto("Ok", ""))))
        given(userDatasource.retrieveUser()).willReturn(user)
        given(activityGsonConverter.convertActivityItems(jsonTemplate, jsonTemplate[0], jsonTemplate[0], user.id)).willReturn(activityFeedList)

        userRepository.getActivityFeed(3, 0, true)
            .test()
            .assertResult(activityFeedList)

        verify(db).runInTransaction(any())
    }

    @Test fun `save user info called`() {
        val firstName = "firstName"
        val lastName = "lastName"

        given(accountNetworkApi.saveUserData(SaveUserDataRequest(firstName, lastName))).willReturn(Single.just(BaseResponse(StatusDto("OK", ""))))

        userRepository.saveUserInfo(firstName, lastName)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    @Test fun `save device token called`() {
        val token = "deviceToken"

        userRepository.saveDeviceToken(token)

        verify(userDatasource).saveIsPushTokenUpdateNeeded(true)
        verify(userDatasource).savePushToken(token)
    }

    @Test fun `verify sms code called`() {
        val code = "1234"

        given(accountNetworkApi.verifySMSCode(VerifyCodeRequest(code))).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.verifySMSCode(code)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userDatasource).saveRegistrationState(OnboardingState.PHONE_NUMBER_CONFIRMED)
    }

    @Test fun `request sms code called`() {
        val blockingTime = 1

        given(accountNetworkApi.requestSMSCode()).willReturn(Single.just(SendSMSResponse(StatusDto("Ok", ""), blockingTime)))

        userRepository.requestSMSCode()
            .test()
            .assertResult(blockingTime)
    }

    @Test fun `save registration state called`() {
        val registrationState = OnboardingState.PHONE_NUMBER_CONFIRMED

        userRepository.saveRegistrationState(registrationState)

        verify(userDatasource).saveRegistrationState(registrationState)
    }

    @Test fun `get registration state called`() {
        val registrationState = OnboardingState.PHONE_NUMBER_CONFIRMED
        given(userDatasource.retrieveRegistratrionState()).willReturn(registrationState)

        assertEquals(registrationState, userRepository.getRegistrationState())
    }

    @Test fun `create user called`() {
        val phoneNumber = "1234"
        val blockingTime = 1
        val expectedResult = UserCreatingCase(false, blockingTime)
        given(accountNetworkApi.createUser(CreateUserRequest(phoneNumber))).willReturn(Single.just(SendSMSResponse(StatusDto("Ok", ""), blockingTime)))

        userRepository.createUser(phoneNumber)
            .test()
            .assertResult(expectedResult)
    }

    @Test fun `clear user data called`() {
        given(db.activityFeedDao()).willReturn(activityFeedDao)
        given(db.projectDao()).willReturn(projectDao)
        given(db.projectDetailsDao()).willReturn(projectDetailsDao)
        given(db.announcementDao()).willReturn(announcementDao)
        given(db.galleryDao()).willReturn(galleryDao)
        given(db.transactionDao()).willReturn(transactionDao)
        given(db.votesHistoryDao()).willReturn(votesHistoryDao)

        userRepository.clearUserData()
            .test()
            .assertComplete()
            .assertNoErrors()

        verify(activityFeedDao).clearTable()
        verify(projectDao).clearTable()
        verify(projectDetailsDao).clearTable()
        verify(announcementDao).clearTable()
        verify(galleryDao).clearTable()
        verify(transactionDao).clearTable()
        verify(votesHistoryDao).clearTable()
        verify(userDatasource).clearUserData()
    }

    @Test fun `register called`() {
        val firstName = "firstName"
        val lastName = "lastName"
        val countryIso = "countryIso"
        val inviteCode = "inviteCode"

        val registrationRequest = RegistrationRequest(inviteCode, RegistrationRequest.UserData(firstName, lastName, countryIso))

        given(accountNetworkApi.register(registrationRequest)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.register(firstName, lastName, countryIso, inviteCode)
            .test()
            .assertResult(true)

        verify(userDatasource).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    @Test fun `register called if inviteCode is null`() {
        val firstName = "firstName"
        val lastName = "lastName"
        val countryIso = "countryIso"
        val inviteCode = ""

        val registrationRequest = RegistrationRequest(null, RegistrationRequest.UserData(firstName, lastName, countryIso))

        given(accountNetworkApi.register(registrationRequest)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.register(firstName, lastName, countryIso, inviteCode)
            .test()
            .assertResult(true)

        verify(userDatasource).saveRegistrationState(OnboardingState.REGISTRATION_FINISHED)
    }

    @Test fun `check app version is called`() {
        val appVersion = "1.0"
        val url = "link"
        val supported = true
        val expectedResult = AppVersion(supported, url)

        given(appVersionProvider.getVersionName()).willReturn(appVersion)
        given(accountNetworkApi.checkVersionSupported(appVersion)).willReturn(Single.just(CheckVersionSupportResponse(supported, url, StatusDto("Ok", ""))))


        userRepository.checkAppVersion()
            .test()
            .assertResult(expectedResult)
    }

    @Test fun `check app version is called link empty`() {
        val appVersion = "1.0"
        val url = null
        val defaultUrl = "defLink"
        val supported = true
        val expectedResult = AppVersion(supported, defaultUrl)

        given(appVersionProvider.getVersionName()).willReturn(appVersion)
        given(appLinkProvider.defaultMarketUrl).willReturn(defaultUrl)
        given(accountNetworkApi.checkVersionSupported(appVersion)).willReturn(Single.just(CheckVersionSupportResponse(supported, url, StatusDto("Ok", ""))))


        userRepository.checkAppVersion()
            .test()
            .assertResult(expectedResult)
    }

    @Test fun `get announcments called updateCached false`() {
        val updateCached = false
        val announcmentsLocal = mutableListOf(AnnouncementLocal(1, "2", "1"))
        val activityFeedAnouncments = mutableListOf(ActivityFeedAnnouncement(announcmentsLocal[0].message, announcmentsLocal[0].publicationDate))
        given(db.announcementDao()).willReturn(announcementDao)
        given(announcementDao.getAnnouncements()).willReturn(Single.just(announcmentsLocal))

        userRepository.getAnnouncements(updateCached)
            .test()
            .assertResult(activityFeedAnouncments)
    }

    @Test fun `get announcments called updateCached true`() {
        val updateCached = true
        val announcmentsLocal = mutableListOf(AnnouncementLocal(0, "2", "1"))
        val activityFeedAnouncments = mutableListOf(ActivityFeedAnnouncement(announcmentsLocal[0].message, announcmentsLocal[0].publicationDate))
        val announcementRemote = mutableListOf(AnnouncementRemote(announcmentsLocal[0].message, announcmentsLocal[0].publicationDate))

        given(db.announcementDao()).willReturn(announcementDao)
        given(activityFeedNetworkApi.getAnnouncements(1, 0)).willReturn(Single.just(AnnouncementResponse(StatusDto("Ok", ""), announcementRemote)))

        userRepository.getAnnouncements(updateCached)
            .test()
            .assertResult(activityFeedAnouncments)

        verify(announcementDao).clearTable()
        verify(announcementDao).insert(announcmentsLocal)
    }

    @Test fun `save parent invite code called`() {
        val parentInviteCode = "parentInviteCode"

        userRepository.saveParentInviteCode(parentInviteCode)

        verify(userDatasource).saveParentInviteCode(parentInviteCode)
    }

    @Test fun `get parent invite code called`() {
        val parentInviteCode = "parentInviteCode"
        given(userDatasource.getParentInviteCode()).willReturn(parentInviteCode)

        userRepository.getParentInviteCode()
            .test()
            .assertResult(parentInviteCode)
    }

    @Test fun `check invite code available called`() {
        val offset = 1
        val offsetHours = TimeUnit.HOURS.convert(offset.toLong(), TimeUnit.MILLISECONDS).toInt()
        val timeZone = mock(TimeZone::class.java)
        val deviceModel = "model"
        val osVersion = "osVersion"
        val screenWidth = 100
        val screenHeight = 200
        val language = "language"
        val country = "country"
        val inviteCode = "inviteCode"
        given(deviceParamsProvider.timezone).willReturn(timeZone)
        given(deviceParamsProvider.model).willReturn(deviceModel)
        given(deviceParamsProvider.osVersion).willReturn(osVersion)
        given(deviceParamsProvider.screenWidth).willReturn(screenWidth)
        given(deviceParamsProvider.screenHeight).willReturn(screenHeight)
        given(deviceParamsProvider.language).willReturn(language)
        given(deviceParamsProvider.country).willReturn(country)
        given(timeZone.rawOffset).willReturn(offset)
        given(accountNetworkApi.checkInviteCodeAvailable(DeviceFingerPrintRemote(deviceModel, osVersion, screenWidth, screenHeight, language, country, offsetHours))).willReturn(Single.just(CheckInviteCodeAvailableResponse(StatusDto("Ok", ""), inviteCode)))

        userRepository.checkInviteCodeAvailable()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userDatasource).saveParentInviteCode(inviteCode)
    }

    @Test fun `enter invite code called`() {
        val inviteCode = "inviteCode"
        given(accountNetworkApi.addInvitationCode(inviteCode)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.enterInviteCode(inviteCode)
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    @Test fun `apply invite code called`() {
        val parentInviteCode = "parentInviteCode"
        given(userDatasource.getParentInviteCode()).willReturn(parentInviteCode)
        given(accountNetworkApi.addInvitationCode(parentInviteCode)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        userRepository.applyInvitationCode()
            .test()
            .assertNoErrors()
            .assertComplete()
    }

    @Test fun `get available languages called`() {
        val languages = mutableListOf(
            Language("ru", R.string.common_russian, R.string.common_russian_native),
            Language("en", R.string.common_english, R.string.common_english_native),
            Language("es", R.string.common_spanish, R.string.common_spanish_native),
            Language("ba", R.string.common_bashkir, R.string.common_bashkir_native)
        )
        given(languagesHolder.getLanguages()).willReturn(languages)
        given(userDatasource.getCurrentLanguage()).willReturn(languages[0].iso)

        userRepository.getAvailableLanguages()
            .test()
            .assertResult(Pair(languages, languages[0].iso))
    }

    @Test fun `change language called`() {
        val language = "ru"

        userRepository.changeLanguage(language)
            .test()
            .assertResult(language)

        verify(userDatasource).changeLanguage(language)
    }
}