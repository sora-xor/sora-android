package jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.polkamarkt

import io.mockk.mockk
import jp.co.soramitsu.feature_blockexplorer_api.data.PiIndexerHealth
import jp.co.soramitsu.feature_blockexplorer_api.data.PiQualifiedRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktReadProvenanceTest {

    @Test
    fun `positions or trades from cache mark the complete screen cached`() {
        val health = mockk<PiIndexerHealth>()

        val usingCache = PolkamarktReadProvenance.requireCoherentAndUsesCache(
            listOf(
                read(health, fromCache = false),
                read(health, fromCache = true),
                read(health, fromCache = false),
            )
        )

        assertTrue(usingCache)
    }

    @Test
    fun `different qualified checkpoints cannot be combined`() {
        val error = assertThrows(IllegalStateException::class.java) {
            PolkamarktReadProvenance.requireCoherentAndUsesCache(
                listOf(
                    read(mockk(), fromCache = false),
                    read(mockk(), fromCache = true),
                )
            )
        }

        assertEquals("PI_INDEXER_POLKAMARKT_CHECKPOINT_CHANGED", error.message)
    }

    private fun read(
        health: PiIndexerHealth,
        fromCache: Boolean,
    ) = PiQualifiedRead(
        value = Unit,
        health = health,
        fromCache = fromCache,
    )
}
