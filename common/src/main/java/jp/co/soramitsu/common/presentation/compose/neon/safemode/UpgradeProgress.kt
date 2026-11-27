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

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.delay

/** What the progress screen adds below the steps as time passes (design spec 9.1-5). */
enum class ProgressPrompt {
    /** Under 10 s: the steps only. */
    NONE,

    /** Show the live status after 10 seconds. */
    STILL_WORKING,

    /** From 60 s: also "Keep waiting" and "Get help". It never fails on its own. */
    OFFER_HELP,
}

const val STILL_WORKING_AFTER_MILLIS = 10_000L
const val OFFER_HELP_AFTER_MILLIS = 60_000L

/**
 * The 10 s / 60 s rule. [keepWaitingAtMillis] is the elapsed time at which the person tapped
 * "Keep waiting"; the help options then come back after another 60 s.
 */
fun progressPrompt(elapsedMillis: Long, keepWaitingAtMillis: Long? = null): ProgressPrompt {
    if (elapsedMillis < STILL_WORKING_AFTER_MILLIS) return ProgressPrompt.NONE
    val since = keepWaitingAtMillis?.let { elapsedMillis - it } ?: elapsedMillis
    return if (since >= OFFER_HELP_AFTER_MILLIS) ProgressPrompt.OFFER_HELP else ProgressPrompt.STILL_WORKING
}

/** An extra action that a waiting step offers, for example "Change node" while offline. */
data class ProgressAction(val label: String, val onClick: () -> Unit)

/**
 * Upgrade progress (design spec 9.3; U1, U2, U5): the shield, a title, a body, the
 * real phases as a StepList and the "keep SORA open" callout. Back is disabled while it shows.
 *
 * [initialElapsedMillis] lets a caller that started the work earlier continue the 10 s / 60 s
 * rule from the right point. [fixedElapsedMillis] freezes the clock (previews and tests only).
 */
@Composable
fun UpgradeProgressContent(
    title: String,
    body: String,
    steps: List<SafeStep>,
    onGetHelp: () -> Unit,
    modifier: Modifier = Modifier,
    stepAction: ProgressAction? = null,
    initialElapsedMillis: Long = 0L,
    fixedElapsedMillis: Long? = null,
    blockBack: Boolean = true,
) {
    val n = MaterialTheme.neon
    BackHandler(enabled = blockBack) { /* Back is disabled while a step is running. */ }
    val startedAt = remember { SystemClock.elapsedRealtime() - initialElapsedMillis }
    var elapsed by remember { mutableLongStateOf(fixedElapsedMillis ?: initialElapsedMillis) }
    // Kept across a recreated screen, like the start time the caller passes in.
    var keepWaitingAt by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(startedAt, fixedElapsedMillis) {
        if (fixedElapsedMillis != null) {
            elapsed = fixedElapsedMillis
            return@LaunchedEffect
        }
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(1_000)
        }
    }
    val prompt = progressPrompt(elapsed, keepWaitingAt)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SafeModeTags.PROGRESS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShieldEmblem()
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
        Spacer(Modifier.size(24.dp))
        SafeCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            StepList(steps)
        }
        if (stepAction != null) {
            Spacer(Modifier.size(12.dp))
            SafeAction(
                text = stepAction.label,
                variant = NeonVariant.Secondary,
                onClick = stepAction.onClick,
            )
        }
        Spacer(Modifier.size(16.dp))
        ReassuranceCallout(stringResource(R.string.safe_mode_keep_open))
        if (prompt != ProgressPrompt.NONE) {
            Spacer(Modifier.size(16.dp))
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SafeModeTags.STILL_WORKING)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                text = stringResource(R.string.safe_mode_still_working),
                style = MaterialTheme.customTypography.paragraphS,
                color = n.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (prompt == ProgressPrompt.OFFER_HELP) {
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SafeAction(
                    text = stringResource(R.string.safe_mode_keep_waiting),
                    variant = NeonVariant.Secondary,
                    onClick = { keepWaitingAt = elapsed },
                )
                SafeAction(
                    text = stringResource(R.string.safe_mode_get_help),
                    variant = NeonVariant.Tertiary,
                    onClick = onGetHelp,
                )
            }
        }
    }
}
