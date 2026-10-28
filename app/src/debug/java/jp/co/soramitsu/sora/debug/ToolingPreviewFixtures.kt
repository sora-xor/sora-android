package jp.co.soramitsu.sora.debug

import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme

/** Target-APK fixtures: PreviewActivity resolves these through the application's class loader. */
@Preview
@Composable
fun PlainWalletToolingPreview() {
    SoraAppTheme(darkTheme = false, reduceEffects = true) {
        Surface {
            Text("Wallet preview ready", Modifier.testTag("tooling-preview-content"))
        }
    }
}

class WalletToolingPreviewProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> get() = sequenceOf("Ready", "Review", "Complete")
}

@Preview
@Composable
fun ParameterizedWalletToolingPreview(
    @PreviewParameter(WalletToolingPreviewProvider::class) state: String,
) {
    SoraAppTheme(darkTheme = false, reduceEffects = true) {
        Surface {
            Text("Wallet state: $state", Modifier.testTag("tooling-preview-content"))
        }
    }
}
