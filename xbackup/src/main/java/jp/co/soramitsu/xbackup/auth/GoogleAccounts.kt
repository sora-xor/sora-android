/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import java.util.concurrent.TimeUnit
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException

internal const val GOOGLE_ACCOUNT_TYPE = "com.google"

/** AccountManager token type for Drive's per-app hidden folder. */
internal const val DRIVE_APPDATA_TOKEN_TYPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

/**
 * The Google account the user picked for Drive backups.
 *
 * Google issues the token for the app's own Android OAuth client (package name
 * and signing certificate), the identity the Play-services sign-in used, so
 * the backups in Drive's appDataFolder stay the same.
 */
internal interface GoogleAccountSource {
    fun current(): Account?

    fun save(accountName: String)

    fun clear()
}

internal class PrefsGoogleAccountSource(context: Context) : GoogleAccountSource {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The saved account, if it is still on the device and visible to the app. */
    override fun current(): Account? {
        val name = prefs.getString(KEY_ACCOUNT_NAME, null) ?: return null
        val account = AccountManager.get(appContext)
            .getAccountsByType(GOOGLE_ACCOUNT_TYPE)
            .firstOrNull { it.name == name }
        if (account == null) clear()
        return account
    }

    override fun save(accountName: String) {
        prefs.edit { putString(KEY_ACCOUNT_NAME, accountName) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_ACCOUNT_NAME) }
    }

    private companion object {
        const val PREFS_NAME = "xbackup_google_account"
        const val KEY_ACCOUNT_NAME = "account_name"
    }
}

/** OAuth access tokens for Drive. */
internal interface DriveTokenSource {
    /**
     * A token for [account]. Throws [AuthConsentException] when the user has to
     * grant access first and [UnauthorizedException] when no token can be had.
     */
    fun token(account: Account): String

    /** Drops a token Drive rejected, so the next [token] call fetches a new one. */
    fun invalidate(token: String)
}

internal class AccountManagerTokenSource(context: Context) : DriveTokenSource {
    private val accountManager = AccountManager.get(context.applicationContext)

    override fun token(account: Account): String {
        val result = try {
            accountManager
                .getAuthToken(account, DRIVE_APPDATA_TOKEN_TYPE, null, false, null, null)
                .getResult(TOKEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: OperationCanceledException) {
            throw UnauthorizedException(e)
        } catch (e: AuthenticatorException) {
            throw UnauthorizedException(e)
        }
        result.getString(AccountManager.KEY_AUTHTOKEN)?.takeIf { it.isNotEmpty() }?.let { return it }
        BundleCompat.getParcelable(result, AccountManager.KEY_INTENT, Intent::class.java)?.let {
            throw AuthConsentException(it)
        }
        throw UnauthorizedException()
    }

    override fun invalidate(token: String) {
        accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, token)
    }

    private companion object {
        const val TOKEN_TIMEOUT_SECONDS = 60L
    }
}
