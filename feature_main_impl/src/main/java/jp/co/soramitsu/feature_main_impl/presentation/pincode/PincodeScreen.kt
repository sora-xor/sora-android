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

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.neon.ProtectWalletSecrets
import jp.co.soramitsu.common.presentation.compose.neon.NeonGlow
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountCheckProgress
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LegacyPinUpgradePanel
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeScreen
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.common.util.ext.shake
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.delay

@Composable
fun PincodeScreen(
    pinCodeScreenState: PinCodeScreenState,
    onNumClick: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onWrongPinAnimationEnd: () -> Unit,
    onSwitchNode: () -> Unit,
    onNewPinContinue: () -> Unit = {},
    onGetHelp: () -> Unit = {},
) {
    ProtectWalletSecrets()
    if (pinCodeScreenState.isLengthInfoAlertVisible) {
        SafeModeScreen { LegacyPinUpgradePanel(onContinue = onNewPinContinue) }
        return
    }
    if (pinCodeScreenState.migrating) {
        AccountCheckProgress(
            connected = pinCodeScreenState.isConnected,
            needsNetwork = pinCodeScreenState.checkNeedsNetwork,
            onSwitchNode = onSwitchNode,
            onGetHelp = onGetHelp,
            silentMillis = 0,
        )
        return
    }
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    LaunchedEffect(pinCodeScreenState.enableShakeAnimation, fx.reduceMotion) {
        if (pinCodeScreenState.enableShakeAnimation && fx.reduceMotion) {
            delay(600)
            onWrongPinAnimationEnd()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(n.neuBg)) {
        val keySize = if (maxWidth >= 400.dp && maxHeight >= 760.dp) 80.dp else 72.dp
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = pinCodeScreenState.toolbarTitleString,
                    textAlign = TextAlign.Center,
                    color = n.textPrimary,
                    style = MaterialTheme.customTypography.headline2,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    DotsProgress(
                        modifier = if (fx.reduceMotion) Modifier else Modifier.shake(
                            enabled = pinCodeScreenState.enableShakeAnimation,
                            onAnimationEnd = onWrongPinAnimationEnd,
                        ),
                        maxDotsCount = pinCodeScreenState.maxDotsCount,
                        checkedDotsCount = pinCodeScreenState.checkedDotsCount,
                        wrong = pinCodeScreenState.enableShakeAnimation,
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                PincodeInputView(
                    keySize = keySize,
                    onNumClick = onNumClick,
                    isDeleteVisible = pinCodeScreenState.isBackButtonVisible,
                    onDeleteClick = onDeleteClick,
                    onClear = { repeat(pinCodeScreenState.maxDotsCount) { onDeleteClick() } },
                    isBiometricVisible = pinCodeScreenState.isBiometricButtonVisible,
                    onBiometricClick = onBiometricClick,
                )
            }
        }

    }
}

@Composable
private fun DotsProgress(modifier: Modifier, maxDotsCount: Int, checkedDotsCount: Int, wrong: Boolean) {
    val n = MaterialTheme.neon
    val entered = checkedDotsCount.coerceIn(0, maxDotsCount)
    val description = stringResource(jp.co.soramitsu.common.R.string.neon_pin_digits_entered, entered, maxDotsCount)
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(maxDotsCount) { index ->
            val filled = index < entered
            Box(Modifier.size(14.dp)) {
                if (filled && !wrong) NeonGlow(n.glowDot, CircleShape, Modifier.matchParentSize())
                Box(
                    Modifier.matchParentSize()
                        .background(if (filled) (if (wrong) n.error else n.cyan) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                        .border(1.5.dp, if (wrong) n.error else if (filled) n.cyan else n.textTertiary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PincodeInputView(
    keySize: Dp,
    onNumClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClear: () -> Unit,
    isDeleteVisible: Boolean,
    onBiometricClick: () -> Unit,
    isBiometricVisible: Boolean,
) {
    Column(
        Modifier.padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { PinTextButton(it, keySize, onNumClick) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (isBiometricVisible) {
                PinIconButton(R.drawable.ic_pin_biometric, keySize, "Biometric",
                    stringResource(jp.co.soramitsu.common.R.string.pincode_touch_fingerprint_scanner), onBiometricClick)
            } else Box(Modifier.size(keySize))
            PinTextButton("0", keySize, onNumClick)
            if (isDeleteVisible) {
                PinIconButton(R.drawable.ic_neu_chevron_back, keySize, "Delete",
                    stringResource(jp.co.soramitsu.common.R.string.common_delete), onDeleteClick, onClear)
            } else Box(Modifier.size(keySize))
        }
    }
}

@Composable
private fun PinTextButton(text: String, keySize: Dp, onClick: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    NeumorphicSurface(
        shape = CircleShape,
        interactionSource = interaction,
        modifier = Modifier.size(keySize).testTagAsId(text).clickable(
            interactionSource = interaction, indication = null, role = Role.Button,
            onClick = { onClick(text) },
        ),
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
            Text(text, color = MaterialTheme.neon.textPrimary, style = NeonType.pinDigit)
        }
    }
}

@Composable
private fun PinIconButton(
    @DrawableRes icon: Int,
    keySize: Dp,
    tag: String,
    description: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val clearLabel = stringResource(jp.co.soramitsu.common.R.string.neon_pin_clear)
    Box(
        Modifier.size(keySize).testTagAsId(tag)
            .combinedClickable(role = Role.Button, onClick = onClick,
                onLongClick = onClear?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } })
            .semantics {
                contentDescription = description
                if (onClear != null) customActions = listOf(CustomAccessibilityAction(clearLabel) { onClear(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null,
            modifier = Modifier.size(if (onClear == null) 28.dp else 24.dp),
            tint = if (onClear == null) MaterialTheme.neon.cyan else MaterialTheme.neon.textSecondary)
    }
}
