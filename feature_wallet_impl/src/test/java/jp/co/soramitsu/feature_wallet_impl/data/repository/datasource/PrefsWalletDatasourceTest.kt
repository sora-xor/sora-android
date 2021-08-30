/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository.datasource

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.feature_wallet_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class PrefsWalletDatasourceTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var preferences: Preferences

    @Mock
    private lateinit var serializer: Serializer

    @Mock
    private lateinit var encryptedPreferences: EncryptedPreferences

    private lateinit var prefsWalletDatasource: PrefsWalletDatasource
    private val emptyJson = "{}"

    @Before
    fun setUp() {
        prefsWalletDatasource = PrefsWalletDatasource(preferences, encryptedPreferences, serializer)
    }

    @Test
    fun `observe migration`() = runBlockingTest {
        prefsWalletDatasource.saveMigrationStatus(MigrationStatus.SUCCESS)
        verify(preferences).putString("key_migration_status", MigrationStatus.SUCCESS.toString())
        val first = prefsWalletDatasource.observeMigrationStatus().first()
        assertEquals(MigrationStatus.SUCCESS, first)
    }

    @Test
    fun `save tx block`() {
        prefsWalletDatasource.saveClaimBlockAndTxHash("block", "hash")
        verify(encryptedPreferences).putEncryptedString("key_claim_block_hash", "block")
        verify(encryptedPreferences).putEncryptedString("key_claim_tx_hash", "hash")
    }

    @Test
    fun `save contacts called`() {
        val keyContacts = "key_contacts"
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        given(serializer.serialize(accounts)).willReturn(emptyJson)

        prefsWalletDatasource.saveContacts(accounts)

        verify(preferences).putString(keyContacts, emptyJson)
    }

    @Test
    fun `retrieve contacts called`() {
        val keyContacts = "key_contacts"
        val accounts = mutableListOf(Account("firstName", "lastName", "accountId"))
        given(preferences.getString(keyContacts)).willReturn(emptyJson)
        given(
            serializer.deserialize<List<Account>>(
                emptyJson,
                object : TypeToken<List<Account>>() {}.type
            )
        ).willReturn(accounts)

        assertEquals(accounts, prefsWalletDatasource.retrieveContacts())
    }

    @Test
    fun `retrieve contacts called if no cached`() {
        val keyContacts = "key_contacts"
        given(preferences.getString(keyContacts)).willReturn("")

        assertNull(prefsWalletDatasource.retrieveContacts())
    }

    @Test
    fun `save invitation parent called`() {
        val keyParentInvitation = "parent_invitation"
        val invitedUser = InvitedUser("firstName", "lastName", Date(1606898968000))
        given(serializer.serialize(invitedUser)).willReturn(emptyJson)

        prefsWalletDatasource.saveInvitationParent(invitedUser)

        verify(encryptedPreferences).putEncryptedString(keyParentInvitation, emptyJson)
    }

    @Test
    fun `retrieve invitation parent called if empty`() {
        val keyParentInvitation = "parent_invitation"
        given(encryptedPreferences.getDecryptedString(keyParentInvitation)).willReturn("")

        assertNull(prefsWalletDatasource.retrieveInvitationParent())
    }

    @Test
    fun `retrieve invitation parent called`() {
        val keyParentInvitation = "parent_invitation"
        val invitedUser = InvitedUser("firstName", "lastName", Date(1606898968000))
        given(serializer.deserialize(emptyJson, InvitedUser::class.java)).willReturn(invitedUser)
        given(encryptedPreferences.getDecryptedString(keyParentInvitation)).willReturn(emptyJson)

        assertEquals(invitedUser, prefsWalletDatasource.retrieveInvitationParent())
    }

    @Test
    fun `save invited users called`() {
        val keyInvitedUsers = "prefs_invited_users"
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName", Date(1606898968000)))
        given(serializer.serialize(invitedUsers)).willReturn(emptyJson)

        prefsWalletDatasource.saveInvitedUsers(invitedUsers)

        verify(preferences).putString(keyInvitedUsers, emptyJson)
    }

    @Test
    fun `retrieve invited users called if empty`() {
        val keyInvitedUsers = "prefs_invited_users"
        given(preferences.getString(keyInvitedUsers)).willReturn("")

        assertNull(prefsWalletDatasource.retrieveInvitedUsers())
    }

    @Test
    fun `retrieve invited users called`() {
        val keyInvitedUsers = "prefs_invited_users"
        val invitedUsers = arrayOf(InvitedUser("firstName", "lastName", Date(1606898968000)))
        given(preferences.getString(keyInvitedUsers)).willReturn(emptyJson)
        given(
            serializer.deserialize<Array<InvitedUser>>(
                emptyJson,
                object : TypeToken<Array<InvitedUser>>() {}.type
            )
        ).willReturn(invitedUsers)

        assertArrayEquals(invitedUsers, prefsWalletDatasource.retrieveInvitedUsers())
    }
}