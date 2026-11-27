/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup.repository

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import java.util.UUID
import jp.co.soramitsu.xbackup.auth.DriveTokenSource
import jp.co.soramitsu.xbackup.auth.GoogleAccountPickerActivity
import jp.co.soramitsu.xbackup.auth.GoogleAccountSource
import jp.co.soramitsu.xbackup.domain.Storage
import jp.co.soramitsu.xbackup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.xbackup.domain.exceptions.InsufficientFilePermissions
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.xbackup.domain.models.FileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Backups in Drive's appDataFolder through the Drive v3 REST API: the same
 * folders, names, descriptions and queries the Google API client used.
 * New backups go into the "backupFolder" folder; backups made by the web app
 * lie directly in appDataFolder.
 */
internal class GoogleDriveStorage(
    private val accounts: GoogleAccountSource,
    private val tokens: DriveTokenSource,
    private val http: DriveHttp = OkHttpDriveHttp(),
    private val apiBase: String = "https://www.googleapis.com",
) : Storage {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun signIn(
        context: Context,
        activityResultLauncher: ActivityResultLauncher<Intent>,
    ): Boolean {
        if (withContext(Dispatchers.IO) { accounts.current() } != null) {
            return true
        }
        activityResultLauncher.launch(GoogleAccountPickerActivity.intent(context))
        return false
    }

    override suspend fun saveFile(
        context: Context,
        name: String,
        description: String,
        file: File,
    ): String = withContext(Dispatchers.IO) {
        val folderId = parentFolder()
        val metadata = JsonObject(
            mapOf(
                "name" to JsonPrimitive(name),
                "description" to JsonPrimitive(description),
                "mimeType" to JsonPrimitive(BACKUP_MIME_TYPE),
                "parents" to JsonArray(listOf(JsonPrimitive(folderId))),
            ),
        )
        val boundary = "sora-" + UUID.randomUUID()
        val body = multipartRelated(boundary, metadata.toString(), file.readBytes())
        val url = url("upload/drive/v3/files")
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id, description, name")
            .build()
        val response = call("POST", url, "multipart/related; boundary=$boundary", body)
        if (response.code == FORBIDDEN && STORAGE_QUOTA_EXCEEDED in reasons(response)) {
            throw StorageQuotaExceeded(DriveApiException(response.code, reasons(response)))
        }
        id(requireSuccess(response))
    }

    override suspend fun getFileContent(
        context: Context,
        fileId: String,
    ): String = withContext(Dispatchers.IO) {
        val url = url("drive/v3/files").addPathSegment(fileId).addQueryParameter("alt", "media").build()
        String(requireSuccess(call("GET", url)).body, Charsets.UTF_8)
    }

    override suspend fun getListOfFileHeads(context: Context): List<FileMeta> = withContext(Dispatchers.IO) {
        val folderId = parentFolder()
        list("'${escape(folderId)}' in parents", "files(id, name, description)")
    }

    override suspend fun getWebListOfFileHeads(context: Context): List<FileMeta> = withContext(Dispatchers.IO) {
        list("'$APP_DATA_FOLDER' in parents and mimeType != '$FOLDER_MIME_TYPE'", "files(id, name, description)")
    }

    override suspend fun deleteFile(
        context: Context,
        fileId: String,
    ) {
        withContext(Dispatchers.IO) {
            val response = call("DELETE", url("drive/v3/files").addPathSegment(fileId).build())
            if (response.code == NOT_FOUND) {
                throw FileNotFoundException(DriveApiException(response.code, reasons(response)))
            }
            requireSuccess(response)
        }
    }

    override suspend fun deleteAllFiles(context: Context) {
        withContext(Dispatchers.IO) {
            val folderId = parentFolder()
            requireSuccess(call("DELETE", url("drive/v3/files").addPathSegment(folderId).build()))
        }
    }

    override suspend fun logout(context: Context) = withContext(Dispatchers.IO) {
        accounts.clear()
    }

    /** The "backupFolder" folder in appDataFolder, created if it is missing. */
    private fun parentFolder(): String {
        list("name = '$BACKUP_FOLDER_NAME'", "files(id)").firstOrNull()?.let { return it.fileId }
        val metadata = JsonObject(
            mapOf(
                "name" to JsonPrimitive(BACKUP_FOLDER_NAME),
                "mimeType" to JsonPrimitive(FOLDER_MIME_TYPE),
                "parents" to JsonArray(listOf(JsonPrimitive(APP_DATA_FOLDER))),
            ),
        )
        val url = url("drive/v3/files").addQueryParameter("fields", "id").build()
        val response = call("POST", url, "application/json; charset=UTF-8", metadata.toString().toByteArray())
        if (response.code == FORBIDDEN && INSUFFICIENT_FILE_PERMISSIONS in reasons(response)) {
            throw InsufficientFilePermissions(DriveApiException(response.code, reasons(response)))
        }
        return id(requireSuccess(response))
    }

    /** Every page of a files.list query in appDataFolder. */
    private fun list(query: String, fields: String): List<FileMeta> {
        val files = mutableListOf<FileMeta>()
        var pageToken: String? = null
        do {
            val url = url("drive/v3/files")
                .addQueryParameter("spaces", APP_DATA_FOLDER)
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken, $fields")
                .addQueryParameter("pageSize", PAGE_SIZE.toString())
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json.parseToJsonElement(String(requireSuccess(call("GET", url)).body, Charsets.UTF_8)).jsonObject
            page["files"]?.jsonArray?.forEach { element ->
                val file = element.jsonObject
                val id = file["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                files += FileMeta(
                    fileName = file["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    description = file["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    fileId = id,
                )
            }
            pageToken = page["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null && files.size < MAX_FILES)
        return files
    }

    /** One authorized request; a rejected token is dropped and the request tried once more. */
    private fun call(method: String, url: HttpUrl, contentType: String? = null, body: ByteArray? = null): DriveResponse {
        val account: Account = accounts.current() ?: throw UnauthorizedException()
        var token = tokens.token(account)
        var response = http.execute(DriveRequest(method, url.toString(), token, contentType, body))
        if (response.code == UNAUTHORIZED) {
            tokens.invalidate(token)
            token = tokens.token(account)
            response = http.execute(DriveRequest(method, url.toString(), token, contentType, body))
            if (response.code == UNAUTHORIZED) throw UnauthorizedException()
        }
        return response
    }

    private fun requireSuccess(response: DriveResponse): DriveResponse {
        if (response.code !in 200..299) throw DriveApiException(response.code, reasons(response))
        return response
    }

    private fun id(response: DriveResponse): String =
        json.parseToJsonElement(String(response.body, Charsets.UTF_8)).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: throw DriveApiException(response.code, listOf("missingId"))

    /** error.errors[].reason of a Drive error body. */
    private fun reasons(response: DriveResponse): List<String> = runCatching {
        json.parseToJsonElement(String(response.body, Charsets.UTF_8)).jsonObject["error"]?.jsonObject
            ?.get("errors")?.jsonArray
            ?.mapNotNull { it.jsonObject["reason"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun url(path: String): HttpUrl.Builder = "$apiBase/$path".toHttpUrl().newBuilder()

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")

    private fun multipartRelated(boundary: String, metadata: String, content: ByteArray): ByteArray {
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: $BACKUP_MIME_TYPE\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        return head.toByteArray() + content + tail.toByteArray()
    }

    private companion object {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val BACKUP_FOLDER_NAME = "backupFolder"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val BACKUP_MIME_TYPE = "text/json"
        const val STORAGE_QUOTA_EXCEEDED = "storageQuotaExceeded"
        const val INSUFFICIENT_FILE_PERMISSIONS = "insufficientFilePermissions"
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val PAGE_SIZE = 1000
        const val MAX_FILES = 10_000
    }
}
