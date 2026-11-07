/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat

/**
 * Lets the user pick a Google account with the system chooser, then asks
 * AccountManager for a Drive app-data token. If Google needs the user's consent
 * first, its screen is opened here and the token is asked for again once that
 * screen closes. The account is saved and RESULT_OK returned only once a token
 * was issued; otherwise RESULT_CANCELED.
 *
 * Callers start it through their ActivityResultLauncher, as they started the
 * Google sign-in screen before, so their result handling does not change.
 */
class GoogleAccountPickerActivity : ComponentActivity() {
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onChosen)
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onConsentClosed() }
    private var accountName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountName = savedInstanceState?.getString(KEY_ACCOUNT_NAME)
        if (savedInstanceState == null) {
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf(GOOGLE_ACCOUNT_TYPE),
                null,
                null,
                null,
                null,
            )
            chooser.launch(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACCOUNT_NAME, accountName)
    }

    private fun onChosen(result: ActivityResult) {
        val data = result.data
        val name = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val type = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (result.resultCode != RESULT_OK || name.isNullOrBlank() || type != GOOGLE_ACCOUNT_TYPE) {
            finishWith(RESULT_CANCELED)
            return
        }
        accountName = name
        requestToken(name, consentAllowed = true)
    }

    /**
     * Google's consent screen does not always report a refusal to AccountManager,
     * so its closing, whatever its result code, is answered by asking once more
     * without opening it again.
     */
    private fun onConsentClosed() {
        accountName?.let { requestToken(it, consentAllowed = false) } ?: finishWith(RESULT_CANCELED)
    }

    private fun requestToken(name: String, consentAllowed: Boolean) {
        AccountManager.get(this).getAuthToken(
            Account(name, GOOGLE_ACCOUNT_TYPE),
            DRIVE_APPDATA_TOKEN_TYPE,
            null,
            false,
            { future ->
                val result = runCatching { future.result }.getOrNull()
                val token = result?.getString(AccountManager.KEY_AUTHTOKEN)
                val consentIntent = result?.let { BundleCompat.getParcelable(it, AccountManager.KEY_INTENT, Intent::class.java) }
                when {
                    !token.isNullOrEmpty() -> {
                        PrefsGoogleAccountSource(this).save(name)
                        finishWith(RESULT_OK)
                    }
                    consentIntent != null && consentAllowed -> consent.launch(consentIntent)
                    else -> finishWith(RESULT_CANCELED)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    private fun finishWith(resultCode: Int) {
        setResult(resultCode)
        finish()
    }

    companion object {
        private const val KEY_ACCOUNT_NAME = "account_name"

        fun intent(context: Context): Intent = Intent(context, GoogleAccountPickerActivity::class.java)
    }
}
