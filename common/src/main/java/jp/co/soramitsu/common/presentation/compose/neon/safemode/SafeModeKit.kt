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

package jp.co.soramitsu.common.presentation.compose.neon.safemode

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonBackground
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonGlow
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonBorder
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography

/*
 * The Safe Mode kit (design spec 7.17, 9): calm, opaque surfaces for recovery and upgrade states.
 * No red, rose or magenta, no grid, no pulse, no caps. Amber is used only for a "needs action"
 * row. Every text pair is a §3 token pair (text.primary / text.secondary on the opaque surface).
 */

/** Test tags shared by every Safe Mode surface (instrumented tests and the debug preview). */
object SafeModeTags {
    const val PANEL = "safeModePanel"
    const val PROGRESS = "safeModeProgress"
    const val STEP_LIST = "safeModeStepList"
    const val STILL_WORKING = "safeModeStillWorking"
    const val TECHNICAL_DETAILS = "safeModeTechnicalDetails"
    const val SHEET = "safeModeSheet"
    const val READ_ONLY_BANNER = "safeModeReadOnlyBanner"
    const val READ_ONLY_REASON = "safeModeReadOnlyReason"
    const val PHRASE_GRID = "safeModePhraseGrid"
    const val PHRASE_VEIL = "safeModePhraseVeil"
}

/**
 * Shield emblem: a glass circle with a 1.5 dp mint ring, a mint shield and a static `glow.safe`.
 * [breathing] (in-progress states only) runs at most five slow cycles, FULL tier and no reduced
 * motion only; it then rests at the static glow.
 */
@Composable
fun ShieldEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    breathing: Boolean = false,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val glow = remember { Animatable(0f) }
    LaunchedEffect(breathing, fx.pulses) {
        glow.snapTo(0f)
        if (!breathing || !fx.pulses) return@LaunchedEffect
        repeat(5) {
            glow.animateTo(1f, tween(1600))
            glow.animateTo(0f, tween(1600))
        }
    }
    Box(
        modifier = modifier
            .size(size + 24.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        NeonGlow(
            spec = n.glowSafe,
            shape = CircleShape,
            modifier = Modifier.size(size),
            alpha = { n.glowSafe.rest + (n.glowSafe.peak - n.glowSafe.rest) * glow.value },
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(n.glassSolid)
                .border(1.5.dp, n.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(size * 32 / 72),
                painter = painterResource(R.drawable.ic_neon_shield_safe),
                tint = n.success,
                contentDescription = null,
            )
        }
    }
}

/**
 * Full-screen Safe Mode host: the safe aurora (cyan and mint only), system-bar padding and a
 * scrolling column of at most 560 dp, centred. The Safe CTA is never pinned, so every option
 * stays visible together (design spec 9.4).
 */
@Composable
fun SafeModeScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        NeonBackground(safe = true)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    content = content,
                )
            }
        }
    }
}

/**
 * SafeCard (design spec 7.17): an opaque surface with the mint-to-cyan hairline. Never glass,
 * never a glow on the card itself.
 */
@Composable
fun SafeCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(n.surface)
            .neonBorder(shape, n.borderSafe, fx)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * SafetyPanel (design spec 9.4): emblem, a title of at most six words, a body of at most three
 * short sentences, optional verified assurances and the reassurance callout, then the actions
 * ([actions], stacked: Safe, Secondary, Tertiary) and anything below them (Technical details).
 */
@Composable
fun SafetyPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    assurances: List<String> = emptyList(),
    callout: String? = null,
    breathing: Boolean = false,
    beforeActions: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    SafeCard(modifier = modifier.testTag(SafeModeTags.PANEL)) {
        ShieldEmblem(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            breathing = breathing,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
            text = title,
            style = MaterialTheme.customTypography.headline1,
            color = n.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = body,
            style = MaterialTheme.customTypography.paragraphM,
            color = n.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (assurances.isNotEmpty()) {
            Spacer(Modifier.size(16.dp))
            assurances.take(3).forEach { AssuranceRow(it) }
        }
        if (callout != null) {
            Spacer(Modifier.size(16.dp))
            ReassuranceCallout(callout)
        }
        beforeActions()
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

/** One verified assurance: a 20 dp cyan check and primary text. */
@Composable
fun AssuranceRow(text: String, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
            painter = painterResource(R.drawable.ic_checkmark_24),
            tint = n.cyan,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.customTypography.textM,
            color = n.textPrimary,
        )
    }
}

/**
 * Reassurance callout (design spec 7.17, 9.1-3): `infoContainer`, radius 16, a cyan shield and
 * primary text. Information, not a warning: no caps, no bold, no warning colour.
 */
@Composable
fun ReassuranceCallout(text: String, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(n.infoContainer)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
            painter = painterResource(R.drawable.ic_neon_shield_safe),
            tint = n.cyan,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.customTypography.textS,
            color = n.textPrimary,
        )
    }
}

/** A full-width Safe Mode action button. [variant] is Safe, Secondary or Tertiary. */
@Composable
fun SafeAction(
    text: String,
    variant: NeonVariant,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    NeonButton(
        modifier = modifier.fillMaxWidth(),
        size = if (variant == NeonVariant.Tertiary) ButtonSize.Small else ButtonSize.Large,
        variant = variant,
        enabled = enabled,
        loading = loading,
        text = text,
        maxLines = 3,
        onClick = onClick,
    )
}

/** One item under Technical details: a label and a value that can be copied. */
data class TechnicalDetail(val label: String, val value: String)

/**
 * Technical details disclosure (design spec 7.17): collapsed by default. The recovery code
 * appears only here, never in a title or body. Each value has its own Copy button.
 */
@Composable
fun TechnicalDetails(
    items: List<TechnicalDetail>,
    onCopy: (TechnicalDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = MaterialTheme.neon
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandedLabel = stringResource(if (expanded) R.string.wallet_expanded else R.string.wallet_collapsed)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag(SafeModeTags.TECHNICAL_DETAILS),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = expandedLabel }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.safe_mode_technical_details),
                style = MaterialTheme.customTypography.buttonM,
                color = n.link,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                painter = painterResource(R.drawable.ic_chevron_down_rounded_16),
                tint = n.link,
                contentDescription = null,
            )
        }
        if (expanded) {
            items.forEach { item ->
                val copyLabel = stringResource(R.string.safe_mode_copy_item, item.label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.customTypography.textXS,
                            color = n.textSecondary,
                        )
                        Text(
                            text = item.value,
                            style = NeonType.mono,
                            color = n.textPrimary,
                        )
                    }
                    NeonButton(
                        modifier = Modifier.semantics { contentDescription = copyLabel },
                        size = ButtonSize.ExtraSmall,
                        variant = NeonVariant.Tertiary,
                        text = stringResource(R.string.safe_mode_copy),
                        onClick = { onCopy(item) },
                    )
                }
            }
        }
    }
}

/** The state of one StepList row (design spec 7.16). */
enum class SafeStepState { DONE, RUNNING, WAITING, NEEDS_ACTION }

/**
 * One StepList row. A row is [SafeStepState.DONE] only after its real check has completed in
 * code; progress is never animated or invented.
 */
data class SafeStep(
    val label: String,
    val state: SafeStepState,
    val detail: String? = null,
)

/** StepList (design spec 7.16): 56 dp rows, a 24 dp state glyph, a 2 dp connecting line. */
@Composable
fun StepList(steps: List<SafeStep>, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Column(modifier = modifier.testTag(SafeModeTags.STEP_LIST)) {
        steps.forEachIndexed { index, step ->
            val stateLabel = stringResource(
                when (step.state) {
                    SafeStepState.DONE -> R.string.safe_mode_step_state_done
                    SafeStepState.RUNNING -> R.string.safe_mode_step_state_running
                    SafeStepState.WAITING -> R.string.safe_mode_step_state_waiting
                    SafeStepState.NEEDS_ACTION -> R.string.safe_mode_step_state_needs_action
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 56.dp)
                    .semantics(mergeDescendants = true) { stateDescription = stateLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The glyph with the connecting 2 dp line above and below it.
                Column(
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .width(2.dp)
                            .background(if (index > 0) n.divider else Color.Transparent),
                    )
                    StepGlyph(step.state)
                    Box(
                        Modifier
                            .weight(1f)
                            .width(2.dp)
                            .background(if (index < steps.lastIndex) n.divider else Color.Transparent),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = step.label,
                        style = MaterialTheme.customTypography.textM,
                        color = n.textPrimary,
                    )
                    step.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.customTypography.textS,
                            color = n.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepGlyph(state: SafeStepState) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    when (state) {
        SafeStepState.DONE -> Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(n.successContainer)
                .border(1.5.dp, n.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_checkmark_24),
                tint = n.success,
                contentDescription = null,
            )
        }

        SafeStepState.RUNNING -> {
            // A cyan arc; it turns on the one shared clock and is static (dashed look) under
            // reduce motion or on MINIMAL.
            val rotation = if (fx.loops) {
                val transition = rememberInfiniteTransition(label = "stepArc")
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                    label = "stepArcRotation",
                ).value
            } else {
                0f
            }
            Canvas(
                Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            ) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    color = n.borderInput.copy(alpha = .35f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                if (fx.loops) {
                    drawArc(
                        color = n.cyan,
                        startAngle = -90f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                } else {
                    repeat(4) { i ->
                        drawArc(
                            color = n.cyan,
                            startAngle = -90f + i * 90f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke),
                        )
                    }
                }
            }
        }

        SafeStepState.WAITING -> Box(
            Modifier
                .size(24.dp)
                .padding(2.dp)
                .border(2.dp, n.borderInput, CircleShape),
        )

        SafeStepState.NEEDS_ACTION -> Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Amber, the only "needs action" colour in Safe Mode. `warning` (not the light
            // `warningFill`) keeps 3:1 against the surface in both themes.
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(n.warning),
            )
        }
    }
}

/**
 * Opaque Safe Mode sheet (design spec 7.10): top radius 28, grabber, `headline2` title and a
 * close button, scrolling body and actions pinned at the bottom. Review and export sheets are
 * always opaque. Never put a recovery phrase in a sheet: a dialog window does not carry the
 * activity's FLAG_SECURE.
 */
@Composable
fun SafeSheet(
    title: String,
    onClose: () -> Unit,
    dismissEnabled: Boolean = true,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    Dialog(
        onDismissRequest = { if (dismissEnabled) onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled,
        ),
    ) {
        // The dialog window draws behind the system bars: keep their icons readable on the scrim.
        val view = LocalView.current
        val dark = n.isDark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(n.scrim)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    enabled = dismissEnabled,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .clip(shape)
                    .background(n.glassSolid)
                    .border(1.dp, n.hairline, shape)
                    // Swallow taps so they do not reach the scrim.
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .testTag(SafeModeTags.SHEET),
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(n.textTertiary.copy(alpha = .6f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                        text = title,
                        style = MaterialTheme.customTypography.headline2,
                        color = n.textPrimary,
                    )
                    if (dismissEnabled) {
                        NeonButton(
                            size = ButtonSize.ExtraSmall,
                            variant = NeonVariant.Tertiary,
                            text = stringResource(R.string.common_close),
                            onClick = onClose,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = footer,
                )
            }
        }
    }
}

/** A checklist row inside a sheet: a glyph and a line of text (design spec 9.5, U11). */
@Composable
fun SafeChecklistRow(
    text: String,
    state: SafeStepState,
    modifier: Modifier = Modifier,
) {
    StepList(listOf(SafeStep(text, state)), modifier)
}
