/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0
*/

package jp.co.soramitsu.xbackup

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import java.io.File
import jp.co.soramitsu.xbackup.auth.DriveTokenSource
import jp.co.soramitsu.xbackup.auth.GoogleAccountSource
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.xbackup.domain.exceptions.InsufficientFilePermissions
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.xbackup.domain.models.FileMeta
import jp.co.soramitsu.xbackup.repository.DriveApiException
import jp.co.soramitsu.xbackup.repository.DriveHttp
import jp.co.soramitsu.xbackup.repository.DriveRequest
import jp.co.soramitsu.xbackup.repository.DriveResponse
import jp.co.soramitsu.xbackup.repository.GoogleDriveStorage
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleDriveStorageTest {

    private class FakeAccounts(var account: Account? = Account("user@example.com", "com.google")) : GoogleAccountSource {
        var cleared = false
        override fun current(): Account? = account
        override fun save(accountName: String) {
            account = Account(accountName, "com.google")
        }
        override fun clear() {
            cleared = true
            account = null
        }
    }

    private class FakeTokens(private val tokens: MutableList<String> = mutableListOf("t1", "t2", "t3")) : DriveTokenSource {
        val invalidated = mutableListOf<String>()
        var consent: Intent? = null
        override fun token(account: Account): String {
            consent?.let { throw AuthConsentException(it) }
            return tokens.removeAt(0)
        }
        override fun invalidate(token: String) {
            invalidated += token
        }
    }

    private class FakeDrive(private val handler: (DriveRequest) -> DriveResponse) : DriveHttp {
        val requests = mutableListOf<DriveRequest>()
        override fun execute(request: DriveRequest): DriveResponse {
            requests += request
            return handler(request)
        }
    }

    private class RecordingLauncher : ActivityResultLauncher<Intent>() {
        val launched = mutableListOf<Intent>()
        override fun launch(input: Intent, options: ActivityOptionsCompat?) {
            launched += input
        }
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = throw UnsupportedOperationException()
    }

    private fun ok(body: String) = DriveResponse(200, body.toByteArray())
    private fun error(code: Int, reason: String) =
        DriveResponse(code, """{"error":{"code":$code,"errors":[{"reason":"$reason"}]}}""".toByteArray())

    private fun query(request: DriveRequest, name: String) = request.url.toHttpUrl().queryParameter(name)
    private fun path(request: DriveRequest) = request.url.toHttpUrl().encodedPath

    @Test
    fun `lists backups in the backup folder across pages`() = runTest {
        val drive = FakeDrive { request ->
            when {
                query(request, "q") == "name = 'backupFolder'" -> ok("""{"files":[{"id":"folder1"}]}""")
                query(request, "pageToken") == null ->
                    ok("""{"files":[{"id":"a","name":"cnA.json","description":"Alice"}],"nextPageToken":"p2"}""")
                else -> ok("""{"files":[{"id":"b","name":"cnB.json"}]}""")
            }
        }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        val files = storage.getListOfFileHeads(dummyContext())

        assertEquals(listOf(FileMeta("cnA.json", "Alice", "a"), FileMeta("cnB.json", "", "b")), files)
        val listing = drive.requests.drop(1)
        assertTrue(listing.all { query(it, "spaces") == "appDataFolder" })
        assertTrue(listing.all { query(it, "q") == "'folder1' in parents" })
        assertEquals(listOf(null, "p2"), listing.map { query(it, "pageToken") })
        assertTrue(drive.requests.all { it.token == "t1" || it.token == "t2" || it.token == "t3" })
    }

    @Test
    fun `web backups are the files directly in appDataFolder`() = runTest {
        val drive = FakeDrive { ok("""{"files":[{"id":"w","name":"Wallet.json","description":"cnW/eth"}]}""") }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        val files = storage.getWebListOfFileHeads(dummyContext())

        assertEquals(listOf(FileMeta("Wallet.json", "cnW/eth", "w")), files)
        assertEquals(
            "'appDataFolder' in parents and mimeType != 'application/vnd.google-apps.folder'",
            query(drive.requests.single(), "q"),
        )
    }

    @Test
    fun `saving creates the backup folder when missing and uploads multipart json`() = runTest {
        val drive = FakeDrive { request ->
            when {
                request.method == "GET" -> ok("""{"files":[]}""")
                path(request) == "/drive/v3/files" -> ok("""{"id":"newFolder"}""")
                else -> ok("""{"id":"file1","name":"cnA.json"}""")
            }
        }
        val file = File.createTempFile("backup", ".json").apply { writeText("""{"address":"cnA"}""") }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        val id = storage.saveFile(dummyContext(), "cnA.json", "Alice", file)

        assertEquals("file1", id)
        val createFolder = drive.requests[1]
        assertEquals("POST", createFolder.method)
        val folderJson = String(createFolder.body!!)
        assertTrue(folderJson.contains("\"mimeType\":\"application/vnd.google-apps.folder\""))
        assertTrue(folderJson.contains("\"parents\":[\"appDataFolder\"]"))
        val upload = drive.requests[2]
        assertEquals("/upload/drive/v3/files", path(upload))
        assertEquals("multipart", query(upload, "uploadType"))
        assertTrue(upload.contentType!!.startsWith("multipart/related; boundary="))
        val body = String(upload.body!!)
        assertTrue(body.contains("\"parents\":[\"newFolder\"]"))
        assertTrue(body.contains("\"name\":\"cnA.json\""))
        assertTrue(body.contains("\"description\":\"Alice\""))
        assertTrue(body.contains("Content-Type: text/json\r\n\r\n{\"address\":\"cnA\"}\r\n"))
        file.delete()
    }

    @Test
    fun `a rejected token is dropped and the request tried once more`() = runTest {
        var calls = 0
        val drive = FakeDrive { if (calls++ == 0) DriveResponse(401, ByteArray(0)) else ok("""{"files":[]}""") }
        val tokens = FakeTokens()
        val storage = GoogleDriveStorage(FakeAccounts(), tokens, drive, "https://drive.test")

        assertEquals(emptyList<FileMeta>(), storage.getWebListOfFileHeads(dummyContext()))
        assertEquals(listOf("t1"), tokens.invalidated)
        assertEquals(listOf("t1", "t2"), drive.requests.map { it.token })
    }

    @Test
    fun `two rejected tokens mean the user is not authorized`() = runTest {
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { DriveResponse(401, ByteArray(0)) }, "https://drive.test")
        expect<UnauthorizedException> { storage.getWebListOfFileHeads(dummyContext()) }
    }

    @Test
    fun `no saved account means the user is not authorized`() = runTest {
        val drive = FakeDrive { ok("{}") }
        val storage = GoogleDriveStorage(FakeAccounts(null), FakeTokens(), drive, "https://drive.test")
        expect<UnauthorizedException> { storage.getWebListOfFileHeads(dummyContext()) }
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun `consent needed is passed to the caller with Google's intent`() = runTest {
        val tokens = FakeTokens().apply { consent = Intent() }
        val storage = GoogleDriveStorage(FakeAccounts(), tokens, FakeDrive { ok("{}") }, "https://drive.test")
        val error = expect<AuthConsentException> { storage.getWebListOfFileHeads(dummyContext()) }
        assertTrue(error.intent === tokens.consent)
    }

    @Test
    fun `quota, permission and missing file errors keep their exceptions`() = runTest {
        val file = File.createTempFile("backup", ".json")
        val quota = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { request ->
            if (request.method == "GET") ok("""{"files":[{"id":"f"}]}""") else error(403, "storageQuotaExceeded")
        }, "https://drive.test")
        expect<StorageQuotaExceeded> { quota.saveFile(dummyContext(), "a.json", "a", file) }

        val permissions = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { request ->
            if (request.method == "GET") ok("""{"files":[]}""") else error(403, "insufficientFilePermissions")
        }, "https://drive.test")
        expect<InsufficientFilePermissions> { permissions.getListOfFileHeads(dummyContext()) }

        val missing = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { error(404, "notFound") }, "https://drive.test")
        expect<FileNotFoundException> { missing.deleteFile(dummyContext(), "gone") }

        val other = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { error(500, "backendError") }, "https://drive.test")
        val failure = expect<DriveApiException> { other.getFileContent(dummyContext(), "x") }
        assertEquals(500, failure.statusCode)
        assertEquals(listOf("backendError"), failure.reasons)
        file.delete()
    }

    @Test
    fun `file content is downloaded with alt media and file ids are path-encoded`() = runTest {
        val drive = FakeDrive { ok("{\"name\":\"x\"}") }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        assertEquals("{\"name\":\"x\"}", storage.getFileContent(dummyContext(), "id/with?odd"))
        val request = drive.requests.single()
        assertEquals("media", query(request, "alt"))
        assertEquals("/drive/v3/files/id%2Fwith%3Fodd", path(request))
    }

    @Test
    fun `deleting everything deletes the backup folder`() = runTest {
        val drive = FakeDrive { request ->
            if (request.method == "GET") ok("""{"files":[{"id":"folder9"}]}""") else DriveResponse(204, ByteArray(0))
        }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        storage.deleteAllFiles(dummyContext())

        assertEquals("DELETE", drive.requests.last().method)
        assertEquals("/drive/v3/files/folder9", path(drive.requests.last()))
    }

    @Test
    fun `deleting a file deletes it by id`() = runTest {
        val drive = FakeDrive { DriveResponse(204, ByteArray(0)) }
        val storage = GoogleDriveStorage(FakeAccounts(), FakeTokens(), drive, "https://drive.test")

        storage.deleteFile(dummyContext(), "f1")

        assertEquals("DELETE", drive.requests.single().method)
        assertEquals("/drive/v3/files/f1", path(drive.requests.single()))
    }

    @Test
    fun `sign in reuses a saved account and otherwise opens the account picker`() = runTest {
        val launcher = RecordingLauncher()
        val signedIn = GoogleDriveStorage(FakeAccounts(), FakeTokens(), FakeDrive { ok("{}") }, "https://drive.test")
        assertTrue(signedIn.signIn(dummyContext(), launcher))
        assertTrue(launcher.launched.isEmpty())

        val accounts = FakeAccounts(null)
        val signedOut = GoogleDriveStorage(accounts, FakeTokens(), FakeDrive { ok("{}") }, "https://drive.test")
        assertFalse(signedOut.signIn(dummyContext(), launcher))
        assertEquals(1, launcher.launched.size)

        signedIn.logout(dummyContext())
        val logoutAccounts = FakeAccounts()
        GoogleDriveStorage(logoutAccounts, FakeTokens(), FakeDrive { ok("{}") }, "https://drive.test").logout(dummyContext())
        assertTrue(logoutAccounts.cleared)
        assertNull(logoutAccounts.current())
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("expected ${T::class.simpleName}, got $e", e)
        }
        fail("expected ${T::class.simpleName}")
        throw AssertionError()
    }

    private fun dummyContext(): Context = DummyContext

    // android.jar stubs return defaults in unit tests, so a bare wrapper is enough.
    private object DummyContext : android.content.ContextWrapper(null)
}
