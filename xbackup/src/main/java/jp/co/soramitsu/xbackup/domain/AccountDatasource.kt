/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.domain

interface AccountDatasource {
    suspend fun saveFileId(
        address: String,
        fileId: String,
    )

    suspend fun getFileId(address: String): String

    suspend fun removeFileId(address: String)

    suspend fun removeAllFields()
}
