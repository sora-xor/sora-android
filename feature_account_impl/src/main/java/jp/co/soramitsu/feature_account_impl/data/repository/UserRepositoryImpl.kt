/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.resourses.Language
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_network_api.domain.model.AppLinksProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_account_api.domain.model.DeviceFingerPrint
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserCreatingCase
import jp.co.soramitsu.feature_account_impl.data.mappers.ActivityGsonConverter
import jp.co.soramitsu.feature_account_impl.data.mappers.mapActivityFeedLocalToActivityFeed
import jp.co.soramitsu.feature_account_impl.data.mappers.mapActivityFeedToActivityFeedLocal
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAnnoucementRemoteToAnnouncement
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAnnouncementLocalToAnnouncement
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAnnouncementToAnnouncementLocal
import jp.co.soramitsu.feature_account_impl.data.mappers.mapCountryDtoToCounty
import jp.co.soramitsu.feature_account_impl.data.mappers.mapDeviceFingerPrintToFingerPrintRemote
import jp.co.soramitsu.feature_account_impl.data.mappers.mapInvitedDtoToInvitedUser
import jp.co.soramitsu.feature_account_impl.data.mappers.mapReputationRemoteToReputation
import jp.co.soramitsu.feature_account_impl.data.mappers.mapUserRemoteToUser
import jp.co.soramitsu.feature_account_impl.data.network.AccountNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.ActivityFeedNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.NotificationNetworkApi
import jp.co.soramitsu.feature_account_impl.data.network.request.CreateUserRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.PushRegistrationRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.RegistrationRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.SaveUserDataRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.TokenChangeRequest
import jp.co.soramitsu.feature_account_impl.data.network.request.VerifyCodeRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDatasource: UserDatasource,
    private val accountNetworkApi: AccountNetworkApi,
    private val notificationNetworkApi: NotificationNetworkApi,
    private val activityFeedNetworkApi: ActivityFeedNetworkApi,
    private val appVersionProvider: AppVersionProvider,
    private val activityGsonConverter: ActivityGsonConverter,
    private val db: AppDatabase,
    private val appLinksProvider: AppLinksProvider,
    private val deviceParamsProvider: DeviceParamsProvider,
    private val languagesHolder: LanguagesHolder
) : UserRepository {

    override fun getAppVersion(): Single<String> {
        return Single.just(appVersionProvider.getVersionName())
    }

    override fun savePin(pin: String) {
        userDatasource.savePin(pin)
    }

    override fun retrievePin(): String {
        return userDatasource.retrievePin()
    }

    override fun updatePushTokenIfNeeded(): Completable {
        return Single.just(userDatasource.isPushTokenUpdateNeeded())
            .flatMapCompletable { updateNeeded ->
                if (updateNeeded) {
                    Single.just(userDatasource.retrievePushToken())
                        .flatMapCompletable { token ->
                            notificationNetworkApi.changeToken(TokenChangeRequest(token, null))
                                .doOnSuccess { userDatasource.saveIsPushTokenUpdateNeeded(false) }
                                .flatMap { notificationNetworkApi.setPermissions(Const.PROJECT_DID) }
                                .onErrorResumeNext {
                                    if (it.message == ResponseCode.CUSTOMER_NOT_FOUND.toString()) {
                                        userDatasource.saveIsPushTokenUpdateNeeded(false)
                                        val tokens = mutableListOf<String>().apply { add(token) }
                                        notificationNetworkApi.registerToken(PushRegistrationRequest(tokens, listOf(*Const.PROJECT_DID)))
                                    } else {
                                        Single.error(it)
                                    }
                                }
                                .ignoreElement()
                        }
                } else {
                    Completable.complete()
                }
            }
    }

    override fun getInvitedUsers(updateCached: Boolean): Single<Invitations> {
        return if (updateCached) {
            getInvitedRemote()
        } else {
            val invitedUsersLocal = userDatasource.retrieveInvitedUsers()
            val parentInvitationLocal = userDatasource.retrieveInvitationParent()
            if (invitedUsersLocal == null) {
                getInvitedRemote()
            } else {
                Single.just(Invitations(invitedUsersLocal.toList(), parentInvitationLocal))
            }
        }
    }

    private fun getInvitedRemote(): Single<Invitations> {
        return accountNetworkApi.getInvitedUsers()
            .map {
                val invitations = it.invitedUsers.map { mapInvitedDtoToInvitedUser(it) }.mapNotNull { it }
                val parent = mapInvitedDtoToInvitedUser(it.parentInfo)
                Invitations(invitations, parent)
            }
            .doOnSuccess {
                userDatasource.saveInvitedUsers(it.acceptedInviteVms.toTypedArray())
                it.parentInvitations?.let { userDatasource.saveInvitationParent(it) }
            }
    }

    override fun getUser(updateCached: Boolean): Single<User> {
        return if (updateCached) {
            getUserRemote()
        } else {
            val userLocal = userDatasource.retrieveUser()
            if (userLocal == null) {
                getUserRemote()
            } else {
                Single.just(userLocal)
            }
        }
    }

    private fun getUserRemote(): Single<User> {
        return accountNetworkApi.getUser()
            .map { mapUserRemoteToUser(it.user) }
            .doOnSuccess { userDatasource.saveUser(it) }
    }

    override fun getInvitationLink(): Single<String> {
        return getUser(false)
            .map { appLinksProvider.inviteUrl + it.values.invitationCode }
    }

    override fun getUserReputation(updateCached: Boolean): Single<Reputation> {
        return if (updateCached) {
            accountNetworkApi.getUserReputation()
                .map { mapReputationRemoteToReputation(it.reputation) }
                .doOnSuccess { userDatasource.saveUserReputation(it) }
        } else {
            Single.just(userDatasource.retrieveUserReputation())
        }
    }

    override fun getActivityFeed(count: Int, offset: Int, updateCached: Boolean): Single<List<ActivityFeed>> {
        return if (updateCached) {
            getActivityFeedRemote(count, offset)
        } else {
            db.activityFeedDao().getActivityFeedList()
                .map { it.map { mapActivityFeedLocalToActivityFeed(it) } }
                .flatMap { activitiesList ->
                    if (activitiesList.isEmpty()) {
                        getActivityFeedRemote(count, offset)
                    } else {
                        Single.just(activitiesList)
                    }
                }
        }
    }

    private fun getActivityFeedRemote(count: Int, offset: Int): Single<List<ActivityFeed>> {
        return activityFeedNetworkApi.getActivityFeed(count, offset)
            .map {
                val projects = it.projectsDict
                val users = it.usersDict
                val userId = userDatasource.retrieveUser()!!.id
                activityGsonConverter.convertActivityItems(it.activity, projects, users, userId)
            }
            .doOnSuccess {
                if (offset == 0) {
                    db.runInTransaction {
                        db.activityFeedDao().clearTable()
                        db.activityFeedDao().insert(it.map { mapActivityFeedToActivityFeedLocal(it) })
                    }
                }
            }
    }

    override fun saveUserInfo(firstName: String, lastName: String): Completable {
        return accountNetworkApi.saveUserData(SaveUserDataRequest(firstName, lastName))
            .ignoreElement()
    }

    override fun saveDeviceToken(notificationToken: String) {
        userDatasource.savePushToken(notificationToken)
        userDatasource.saveIsPushTokenUpdateNeeded(true)
    }

    override fun verifySMSCode(code: String): Completable {
        return accountNetworkApi.verifySMSCode(VerifyCodeRequest(code))
            .doOnSuccess { userDatasource.saveRegistrationState(OnboardingState.PHONE_NUMBER_CONFIRMED) }
            .ignoreElement()
    }

    override fun requestSMSCode(): Single<Int> {
        return accountNetworkApi.requestSMSCode()
            .map { it.blockingTime }
    }

    override fun saveRegistrationState(onboardingState: OnboardingState) {
        userDatasource.saveRegistrationState(onboardingState)
    }

    override fun getRegistrationState(): OnboardingState {
        return userDatasource.retrieveRegistratrionState()
    }

    override fun createUser(phoneNumber: String): Single<UserCreatingCase> {
        return accountNetworkApi.createUser(CreateUserRequest(phoneNumber))
            .map { UserCreatingCase(false, it.blockingTime) }
            .onErrorResumeNext {
                when {
                    it.message == ResponseCode.PHONE_ALREADY_VERIFIED.toString() -> Single.just(UserCreatingCase(true, 0))
                    else -> Single.error(it)
                }
            }
    }

    override fun clearUserData(): Completable {
        return Completable.fromAction {
            userDatasource.clearUserData()
            db.activityFeedDao().clearTable()
            db.announcementDao().clearTable()
            db.galleryDao().clearTable()
            db.projectDao().clearTable()
            db.projectDetailsDao().clearTable()
            db.transactionDao().clearTable()
            db.votesHistoryDao().clearTable()
        }
    }

    override fun getAllCountries(): Single<List<Country>> {
        return accountNetworkApi.getAllCountries()
            .map { mapCountryDtoToCounty(it.countryRemote) }
    }

    override fun register(firstName: String, lastName: String, countryIso: String, inviteCode: String): Single<Boolean> {
        return accountNetworkApi.register(
            RegistrationRequest(
                if (inviteCode.isEmpty()) null else inviteCode,
                RegistrationRequest.UserData(
                    firstName,
                    lastName,
                    countryIso
                )))
            .map { true }
            .doOnSuccess { userDatasource.saveRegistrationState(OnboardingState.REGISTRATION_FINISHED) }
            .onErrorResumeNext {
                if (it.message == ResponseCode.INVITATION_CODE_NOT_FOUND.toString()) {
                    Single.just(false)
                } else {
                    Single.error(it)
                }
            }
    }

    override fun checkAppVersion(): Single<AppVersion> {
        return accountNetworkApi.checkVersionSupported(appVersionProvider.getVersionName())
            .map { AppVersion(it.result, it.url ?: appLinksProvider.defaultMarketUrl) }
    }

    override fun getAnnouncements(updateCached: Boolean): Single<List<ActivityFeedAnnouncement>> {
        return if (updateCached) {
            getAnnouncementsRemote()
        } else {
            db.announcementDao().getAnnouncements()
                .map { it.map { mapAnnouncementLocalToAnnouncement(it) } }
        }
    }

    private fun getAnnouncementsRemote(): Single<List<ActivityFeedAnnouncement>> {
        return activityFeedNetworkApi.getAnnouncements(1, 0)
            .map { it.announcements.map { mapAnnoucementRemoteToAnnouncement(it) } }
            .doOnSuccess {
                db.announcementDao().clearTable()
                db.announcementDao().insert(it.map { mapAnnouncementToAnnouncementLocal(it) })
            }
    }

    override fun saveParentInviteCode(inviteCode: String) {
        userDatasource.saveParentInviteCode(inviteCode)
    }

    override fun getParentInviteCode(): Single<String> {
        return Single.just(userDatasource.getParentInviteCode())
    }

    override fun checkInviteCodeAvailable(): Completable {
        val offsetInHours = TimeUnit.HOURS.convert(deviceParamsProvider.timezone.rawOffset.toLong(), TimeUnit.MILLISECONDS)
        val deviceFingerPrint = DeviceFingerPrint(deviceParamsProvider.model, deviceParamsProvider.osVersion, deviceParamsProvider.screenWidth, deviceParamsProvider.screenHeight,
            deviceParamsProvider.language, deviceParamsProvider.country, offsetInHours.toInt())
        return accountNetworkApi.checkInviteCodeAvailable(mapDeviceFingerPrintToFingerPrintRemote(deviceFingerPrint))
            .map { it.invitationCode }
            .onErrorResumeNext {
                if (it is SoraException && SoraException.Kind.BUSINESS == it.kind) {
                    if (ResponseCode.AMBIGUOUS_RESULT == it.errorResponseCode || ResponseCode.INVITATION_CODE_NOT_FOUND == it.errorResponseCode) {
                        Single.just("")
                    } else {
                        Single.error(it)
                    }
                } else {
                    Single.error(it)
                }
            }
            .doOnSuccess { if (it.isNotEmpty()) userDatasource.saveParentInviteCode(it) }
            .ignoreElement()
    }

    override fun enterInviteCode(inviteCode: String): Completable {
        return accountNetworkApi.addInvitationCode(inviteCode)
            .ignoreElement()
    }

    override fun applyInvitationCode(): Completable {
        return getParentInviteCode()
            .flatMapCompletable { enterInviteCode(it) }
    }

    override fun getAvailableLanguages(): Single<Pair<List<Language>, String>> {
        return Single.just(languagesHolder.getLanguages())
            .map { languages ->
                val currentLanguage = userDatasource.getCurrentLanguage()
                Pair(languages, currentLanguage)
            }
    }

    override fun changeLanguage(language: String): Single<String> {
        return Single.fromCallable { userDatasource.changeLanguage(language) }
            .map { language }
    }

    override fun getSelectedLanguage(): Single<Language> {
        return Single.just(languagesHolder.getLanguages())
            .map { languages ->
                val currentLanguage = userDatasource.getCurrentLanguage()
                languages.first { it.iso == currentLanguage }
            }
    }
}