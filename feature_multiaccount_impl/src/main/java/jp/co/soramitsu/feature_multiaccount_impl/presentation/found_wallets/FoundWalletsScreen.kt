package jp.co.soramitsu.feature_multiaccount_impl.presentation.found_wallets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.asciiPinDigits
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

/** Test tags of "Wallets found on this phone" in onboarding. */
internal object FoundWalletsTags {
    const val SCREEN = "FoundWalletsScreen"
    const val PIN = "FoundWalletsPin"
    const val RESTORE = "FoundWalletsRestore"
    const val WRONG_PIN = "FoundWalletsWrongPin"
}

/**
 * "Wallets found on this phone", reached from the welcome screen (design spec 9.2, U8). The keys
 * of the listed wallets are on this phone without an account, for example after "Set up this
 * device again" or when the app's database was lost. The PIN that was used in SORA on this phone
 * is asked for before anything is restored; leaving the screen changes nothing.
 */
@Composable
internal fun FoundWalletsScreen(
    state: OnboardingViewModel.FoundWalletsState,
    onRestore: (pin: String) -> Unit,
) {
    val n = MaterialTheme.neon
    // Held in memory only: the typed PIN is never written to the saved instance state.
    var pin by remember { mutableStateOf("") }
    SafeCard(
        modifier = Modifier
            .padding(horizontal = Dimens.x2, vertical = Dimens.x1)
            .testTag(FoundWalletsTags.SCREEN),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.safe_mode_retained_title),
            style = MaterialTheme.customTypography.headline2,
            color = n.textPrimary,
        )
        Spacer(Modifier.size(Dimens.x1))
        Text(
            text = stringResource(R.string.onboarding_found_wallets_body),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        state.addresses.forEach { address ->
            Spacer(Modifier.size(Dimens.x1))
            Text(
                text = if (address.length > 16) "${address.take(8)}…${address.takeLast(8)}" else address,
                style = NeonType.mono,
                color = n.textPrimary,
            )
        }
        Spacer(Modifier.size(Dimens.x2))
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FoundWalletsTags.PIN),
            value = pin,
            onValueChange = { value -> pin = asciiPinDigits(value) },
            enabled = !state.working,
            singleLine = true,
            label = { Text(text = stringResource(id = R.string.onboarding_found_wallets_pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = state.wrongPin,
        )
        if (state.wrongPin) {
            Text(
                modifier = Modifier.testTag(FoundWalletsTags.WRONG_PIN),
                text = stringResource(id = R.string.account_repair_pin_wrong),
                color = MaterialTheme.customColors.statusError,
                style = MaterialTheme.customTypography.textXS,
            )
        }
        Spacer(Modifier.size(Dimens.x2))
        SafeAction(
            modifier = Modifier.testTag(FoundWalletsTags.RESTORE),
            text = stringResource(R.string.onboarding_found_wallets_restore),
            variant = NeonVariant.Safe,
            enabled = pin.isNotEmpty() && !state.working,
            loading = state.working,
            onClick = {
                val entered = pin
                pin = ""
                onRestore(entered)
            },
        )
    }
}
