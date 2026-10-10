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

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Effect tiers (design spec 4.0). Decided once per device state and read everywhere through
 * [LocalFx]; components never check the API level themselves.
 *
 * - FULL: glow, finite pulses, cross-window blur behind dialogs (API 31+ only).
 * - REDUCED: everything static; no pulses, no live blur.
 * - MINIMAL: opaque cards, 1.5 dp rings instead of glow, flat neumorphic faces, aurora only.
 */
enum class FxTier { FULL, REDUCED, MINIMAL }

@Immutable
data class FxPolicy(
    val tier: FxTier,
    val reduceMotion: Boolean,
    val opaqueGlass: Boolean,
    val highContrast: Boolean,
) {
    /** Coloured glow layers. Off under high contrast (borders carry the state instead). */
    val glows: Boolean get() = !highContrast

    /** Cross-window blur behind dialogs and sheets. */
    val realBlur: Boolean get() = tier == FxTier.FULL && !opaqueGlass && !highContrast

    /** Finite CTA pulse and Safe Mode breathing. */
    val pulses: Boolean get() = tier == FxTier.FULL && !reduceMotion

    /** Grain and grid textures. */
    val ambientDetail: Boolean get() = tier != FxTier.MINIMAL

    /** Neumorphic shadows and the light-theme card shadow. */
    val shadows: Boolean get() = tier != FxTier.MINIMAL

    /** Looping motion (shimmer sweep, rotating pending rings). */
    val loops: Boolean get() = !reduceMotion && tier != FxTier.MINIMAL

    companion object {
        /** Safe default used before a device has been measured (and in previews). */
        val Default = FxPolicy(FxTier.REDUCED, reduceMotion = false, opaqueGlass = false, highContrast = false)

        fun from(signals: DeviceFxSignals, reduceEffects: Boolean): FxPolicy {
            val tier = resolveFxTier(
                sdkInt = signals.sdkInt,
                isLowRamDevice = signals.isLowRamDevice,
                totalMemBytes = signals.totalMemBytes,
                powerSave = signals.powerSave,
                reduceEffects = reduceEffects,
            )
            return FxPolicy(
                tier = tier,
                reduceMotion = !signals.animatorsEnabled,
                opaqueGlass = tier == FxTier.MINIMAL,
                highContrast = signals.highContrast,
            )
        }
    }
}

val LocalFx = staticCompositionLocalOf { FxPolicy.Default }

private const val GIB = 1L shl 30
internal val FULL_MIN_MEM_BYTES = (3.5 * GIB).toLong()
internal val REDUCED_MIN_MEM_BYTES = (1.8 * GIB).toLong()

/**
 * Pure tier rule (design spec 4.0), kept separate from Android services so it is unit-tested.
 * A total memory of 0 means "unknown" and never lowers the tier on its own.
 */
fun resolveFxTier(
    sdkInt: Int,
    isLowRamDevice: Boolean,
    totalMemBytes: Long,
    powerSave: Boolean,
    reduceEffects: Boolean,
): FxTier {
    val memKnown = totalMemBytes > 0
    return when {
        reduceEffects ||
            sdkInt < Build.VERSION_CODES.P ||
            isLowRamDevice ||
            (memKnown && totalMemBytes < REDUCED_MIN_MEM_BYTES) -> FxTier.MINIMAL

        sdkInt < Build.VERSION_CODES.S ||
            powerSave ||
            (memKnown && totalMemBytes < FULL_MIN_MEM_BYTES) -> FxTier.REDUCED

        else -> FxTier.FULL
    }
}

/** What the device says right now. Re-read on resume and when power-save mode changes. */
@Immutable
data class DeviceFxSignals(
    val sdkInt: Int,
    val isLowRamDevice: Boolean,
    val totalMemBytes: Long,
    val powerSave: Boolean,
    val animatorsEnabled: Boolean,
    val highContrast: Boolean,
) {
    companion object {
        fun read(context: Context): DeviceFxSignals {
            val am = context.getSystemService(ActivityManager::class.java)
            val pm = context.getSystemService(PowerManager::class.java)
            val totalMem = runCatching {
                ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem
            }.getOrDefault(0L)
            return DeviceFxSignals(
                sdkInt = Build.VERSION.SDK_INT,
                isLowRamDevice = am?.isLowRamDevice == true,
                totalMemBytes = totalMem,
                powerSave = pm?.isPowerSaveMode == true,
                animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
                highContrast = readHighContrast(context),
            )
        }

        private fun readHighContrast(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                (context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f) > 0f
    }
}

/**
 * One process-wide reader of [DeviceFxSignals]: a single power-save receiver for the whole app
 * instead of one per themed screen.
 */
internal object DeviceFxMonitor {

    private var flow: MutableStateFlow<DeviceFxSignals>? = null

    @Synchronized
    fun signals(context: Context): StateFlow<DeviceFxSignals> {
        flow?.let { return it.asStateFlow() }
        val app = context.applicationContext
        val created = MutableStateFlow(DeviceFxSignals.read(app))
        flow = created
        runCatching {
            ContextCompat.registerReceiver(
                app,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        refresh(app)
                    }
                },
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        return created.asStateFlow()
    }

    fun refresh(context: Context) {
        val target = synchronized(this) { flow } ?: return
        target.value = DeviceFxSignals.read(context.applicationContext)
    }
}

/**
 * The in-app "Reduce effects" switch (Settings, Appearance). Persisted by
 * [jp.co.soramitsu.common.domain.DarkThemeManager] under its own key `neon_reduce_effects`;
 * this holder only shares the current value with every `SoraAppTheme`.
 */
object NeonEffectsPreference {

    const val KEY = "neon_reduce_effects"

    private val state = MutableStateFlow(false)

    val reduceEffects: StateFlow<Boolean> = state.asStateFlow()

    fun update(reduceEffects: Boolean) {
        state.value = reduceEffects
    }
}

@Composable
fun rememberFxPolicy(reduceEffects: Boolean): FxPolicy {
    val context = LocalContext.current.applicationContext
    val signals by remember(context) { DeviceFxMonitor.signals(context) }.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) DeviceFxMonitor.refresh(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(signals, reduceEffects) { FxPolicy.from(signals, reduceEffects) }
}
