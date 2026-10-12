/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause
*/

package jp.co.soramitsu.common.domain

/**
 * The account has no signing key stored on this phone, so it cannot sign here. Trying again does
 * not change that. It says nothing about the account's funds, and nothing is changed by raising
 * it: no key, preference or account row is touched.
 *
 * A key that is stored but cannot be decrypted is reported by the storage layer itself
 * ([jp.co.soramitsu.common.util.WalletDecryptionException], or a
 * [jp.co.soramitsu.common.util.KeyMaterialUnavailableException] that is not transient).
 */
class SigningKeyUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
