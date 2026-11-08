/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.xbackup.auth.AccountManagerTokenSource
import jp.co.soramitsu.xbackup.auth.DRIVE_APPDATA_TOKEN_TYPE
import jp.co.soramitsu.xbackup.auth.GOOGLE_ACCOUNT_TYPE
import jp.co.soramitsu.xbackup.auth.PrefsGoogleAccountSource
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GoogleAccountsTest {
    private val accountManager = mockk<AccountManager>(relaxed = true)
    private val future = mockk<AccountManagerFuture<Bundle>>()
    private val result = mockk<Bundle>()

    @Before
    fun setUp() {
        TestContext.preferences.clear()
        mockkStatic(AccountManager::class)
        every { AccountManager.get(any()) } returns accountManager
        every {
            accountManager.getAuthToken(
                any<Account>(),
                DRIVE_APPDATA_TOKEN_TYPE,
                isNull<Bundle>(),
                false,
                isNull<AccountManagerCallback<Bundle>>(),
                isNull<Handler>(),
            )
        } returns future
        every { future.getResult(60, TimeUnit.SECONDS) } returns result
        every { result.getString(AccountManager.KEY_AUTHTOKEN) } returns null
        @Suppress("DEPRECATION")
        every { result.getParcelable<Parcelable>(AccountManager.KEY_INTENT) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(AccountManager::class)
    }

    /** android.jar stubs skip constructor bodies, so the public fields are set here. */
    private fun google(name: String) = Account(name, GOOGLE_ACCOUNT_TYPE).also {
        Account::class.java.getField("name").apply { isAccessible = true }.set(it, name)
        Account::class.java.getField("type").apply { isAccessible = true }.set(it, GOOGLE_ACCOUNT_TYPE)
    }

    private fun savedName() = TestContext.preferences["xbackup_google_account"]?.getString("account_name", null)

    @Test
    fun `nothing saved means no account and no AccountManager lookup`() {
        assertNull(PrefsGoogleAccountSource(TestContext).current())
        verify(exactly = 0) { accountManager.getAccountsByType(any()) }
    }

    @Test
    fun `the saved account is current while it is on the device`() {
        val user = google("user@example.com")
        every { accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE) } returns arrayOf(google("other@example.com"), user)
        val source = PrefsGoogleAccountSource(TestContext)

        source.save("user@example.com")
        assertSame(user, source.current())

        source.clear()
        assertNull(source.current())
        assertNull(savedName())
    }

    @Test
    fun `an account removed from the device is forgotten`() {
        every { accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE) } returns emptyArray()
        val source = PrefsGoogleAccountSource(TestContext)
        source.save("user@example.com")
        assertEquals("user@example.com", savedName())

        assertNull(source.current())
        assertNull(savedName())
    }

    @Test
    fun `an issued token is returned`() {
        every { result.getString(AccountManager.KEY_AUTHTOKEN) } returns "token"
        assertEquals("token", AccountManagerTokenSource(TestContext).token(google("user@example.com")))
    }

    @Test
    fun `Google's consent screen is handed to the caller`() {
        val consent = Intent()
        every { result.getString(AccountManager.KEY_AUTHTOKEN) } returns ""
        @Suppress("DEPRECATION")
        every { result.getParcelable<Parcelable>(AccountManager.KEY_INTENT) } returns consent

        val error = assertThrows(AuthConsentException::class.java) {
            AccountManagerTokenSource(TestContext).token(google("user@example.com"))
        }
        assertSame(consent, error.intent)
    }

    @Test
    fun `no token and no consent screen means unauthorized`() {
        assertThrows(UnauthorizedException::class.java) {
            AccountManagerTokenSource(TestContext).token(google("user@example.com"))
        }
    }

    @Test
    fun `a cancelled or failed authenticator means unauthorized`() {
        val source = AccountManagerTokenSource(TestContext)
        every { future.getResult(60, TimeUnit.SECONDS) } throws OperationCanceledException()
        assertThrows(UnauthorizedException::class.java) { source.token(google("user@example.com")) }

        every { future.getResult(60, TimeUnit.SECONDS) } throws AuthenticatorException()
        assertThrows(UnauthorizedException::class.java) { source.token(google("user@example.com")) }
    }

    @Test
    fun `a rejected token is invalidated`() {
        AccountManagerTokenSource(TestContext).invalidate("stale")
        verify { accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, "stale") }
    }
}
