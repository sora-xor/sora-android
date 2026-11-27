/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.domain

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import jp.co.soramitsu.xbackup.domain.models.FileMeta

internal interface Storage {
    suspend fun signIn(
        context: Context,
        activityResultLauncher: ActivityResultLauncher<Intent>,
    ): Boolean

    suspend fun saveFile(
        context: Context,
        name: String,
        description: String,
        file: File,
    ): String

    suspend fun getFileContent(
        context: Context,
        fileId: String,
    ): String

    suspend fun getListOfFileHeads(context: Context): List<FileMeta>

    suspend fun getWebListOfFileHeads(context: Context): List<FileMeta>

    suspend fun deleteFile(
        context: Context,
        fileId: String,
    )

    suspend fun deleteAllFiles(context: Context)

    suspend fun logout(context: Context)
}
