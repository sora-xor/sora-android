/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.domain.exceptions

import android.content.Intent

class UnauthorizedException(e: Exception? = null) : Throwable(e)

class DecryptionException(e: Exception? = null) : Throwable(e)

class DecodingException(e: Exception? = null) : Throwable(e)

/** Google needs the user to grant Drive access again: launch [intent], then retry. */
class AuthConsentException(val intent: Intent, cause: Throwable? = null) : Throwable(cause)

class FileNotFoundException(e: Exception) : Throwable(e)

class StorageQuotaExceeded(e: Exception) : Throwable(e)

class InsufficientFilePermissions(e: Exception) : Throwable(e)
