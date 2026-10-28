package jp.co.soramitsu.common.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** A private DataStore file per test keeps wallet preferences untouched. */
class PreferenceValueContractTest {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "values-${UUID.randomUUID()}.preferences_pb")
    private val preferences = SoraPreferences(PreferenceDataStoreFactory.create(scope = scope) { file })
    @After fun cleanup() = runBlocking { job.cancelAndJoin(); file.delete(); Unit }
    @Test fun booleansDistinguishMissingFromExplicitFalseAndPublishOneAtomicSnapshot() = runBlocking {
        val keys = setOf("visible", "send")
        assertEquals(emptyMap<String, Boolean>(), preferences.getBooleanSnapshot(keys))
        assertFalse(preferences.containsBoolean("visible"))
        preferences.putBooleans(mapOf("visible" to true, "send" to false))
        assertEquals(mapOf("visible" to true, "send" to false), preferences.getBooleanSnapshot(keys))
        assertTrue(preferences.containsBoolean("send")); assertFalse(preferences.getBoolean("send", true))
        val ready = CompletableDeferred<Unit>()
        val snapshots = async { preferences.getBooleanSnapshotFlow(keys).onEach { ready.complete(Unit) }.take(2).toList() }
        ready.await(); preferences.putBooleans(mapOf("visible" to false, "send" to true))
        assertEquals(listOf(mapOf("visible" to true, "send" to false), mapOf("visible" to false, "send" to true)), withTimeout(5_000) { snapshots.await() })
    }
    @Test fun snapshotRejectsEmptyExcessiveAndInvalidKeySetsBeforeReading() {
        for (keys in listOf(emptySet(), (0..32).map { "key$it" }.toSet(), setOf(" "), setOf("x".repeat(257)))) {
            assertTrue(runCatching { preferences.getBooleanSnapshotFlow(keys) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
    @Test fun integerFlowReflectsPersistedValueAndExistingDefaultLookupDoesNotReplaceIt() = runBlocking {
        assertEquals(7, preferences.getIntFlow("count", 7).first())
        preferences.putInt("count", 42)
        assertEquals(42, preferences.getIntFlow("count", -1).first())
        assertEquals(42, preferences.getOrPutInt("count", -1))
        assertEquals(42, preferences.getInt("count", -1))
    }
    @Test fun floatsAndDoublesKeepSignedZeroAndSmallValuesAcrossDiskReads() = runBlocking {
        assertEquals(1.5f, preferences.getFloat("float", 1.5f))
        for (value in listOf(-0.0f, Float.MIN_VALUE, 1.25f)) {
            preferences.putFloat("float", value)
            assertEquals(value.toRawBits(), preferences.getFloat("float", 0f).toRawBits())
        }
        for (value in listOf(-0.0, Double.MIN_VALUE, 123456789.125)) {
            preferences.putDouble("double", value)
            assertEquals(value.toRawBits(), preferences.getDouble("double", 0.0).toRawBits())
        }
    }
    @Test fun longValuesRoundTripWithoutDoublePrecisionLoss() = runBlocking {
        assertEquals(-1L, preferences.getLong("height", -1))
        for (value in listOf(9_007_199_254_740_993L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            preferences.putLong("height", value); assertEquals(value, preferences.getLong("height", 0))
        }
    }
    @Test fun clearingOneStringDoesNotDeleteUnrelatedPreferenceTypes() = runBlocking {
        preferences.putString("name", "Public fixture"); preferences.putInt("count", 42)
        preferences.putBoolean("enabled", true); preferences.clear("name")
        assertEquals("", preferences.getString("name")); assertEquals(42, preferences.getInt("count", 0))
        assertTrue(preferences.getBooleanFlow("enabled", false).first())
    }
}
