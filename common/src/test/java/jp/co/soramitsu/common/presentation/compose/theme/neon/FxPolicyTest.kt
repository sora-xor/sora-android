/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
