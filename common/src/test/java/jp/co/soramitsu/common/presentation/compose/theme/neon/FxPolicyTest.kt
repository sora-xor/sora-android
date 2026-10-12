/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.presentation.compose.theme.neon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxPolicyTest {

    private val gib = 1L shl 30

    private fun tier(
        sdk: Int = 34,
        lowRam: Boolean = false,
        mem: Long = 8 * gib,
        powerSave: Boolean = false,
        reduce: Boolean = false,
    ) = resolveFxTier(sdk, lowRam, mem, powerSave, reduce)

    @Test
    fun `modern phone gets full effects`() {
        assertEquals(FxTier.FULL, tier())
        assertEquals(FxTier.FULL, tier(sdk = 31, mem = (3.5 * gib).toLong()))
    }

    @Test
    fun `api 28 to 30, power save or under 3_5 GiB reduce effects`() {
        assertEquals(FxTier.REDUCED, tier(sdk = 28))
        assertEquals(FxTier.REDUCED, tier(sdk = 30))
        assertEquals(FxTier.REDUCED, tier(powerSave = true))
        assertEquals(FxTier.REDUCED, tier(mem = 3 * gib))
    }

    @Test
    fun `api 26 and 27, low ram, under 1_8 GiB or the setting give minimal effects`() {
        assertEquals(FxTier.MINIMAL, tier(sdk = 26))
        assertEquals(FxTier.MINIMAL, tier(sdk = 27))
        assertEquals(FxTier.MINIMAL, tier(lowRam = true))
        assertEquals(FxTier.MINIMAL, tier(mem = gib))
        assertEquals(FxTier.MINIMAL, tier(reduce = true))
        assertEquals(FxTier.MINIMAL, tier(sdk = 36, mem = 16 * gib, reduce = true))
    }

    @Test
    fun `unknown memory never lowers the tier by itself`() {
        assertEquals(FxTier.FULL, tier(mem = 0))
    }

    @Test
    fun `policy flags follow the tier and system settings`() {
        val signals = DeviceFxSignals(
            sdkInt = 35,
            isLowRamDevice = false,
            totalMemBytes = 8 * gib,
            powerSave = false,
            animatorsEnabled = true,
            highContrast = false,
        )
        val full = FxPolicy.from(signals, reduceEffects = false)
        assertEquals(FxTier.FULL, full.tier)
        assertTrue(full.pulses)
        assertTrue(full.realBlur)
        assertTrue(full.glows)
        assertFalse(full.opaqueGlass)

        val noMotion = FxPolicy.from(signals.copy(animatorsEnabled = false), reduceEffects = false)
        assertTrue(noMotion.reduceMotion)
        assertFalse(noMotion.pulses)
        assertFalse(noMotion.loops)

        val reduced = FxPolicy.from(signals, reduceEffects = true)
        assertEquals(FxTier.MINIMAL, reduced.tier)
        assertTrue(reduced.opaqueGlass)
        assertTrue(reduced.reduceMotion)
        assertFalse(reduced.loops)
        assertFalse(reduced.realBlur)
        assertFalse(reduced.shadows)
        assertFalse(reduced.ambientDetail)

        val contrast = FxPolicy.from(signals.copy(highContrast = true), reduceEffects = false)
        assertFalse(contrast.glows)
        assertFalse(contrast.realBlur)
    }
}
