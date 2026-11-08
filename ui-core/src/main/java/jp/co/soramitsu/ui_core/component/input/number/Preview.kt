package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.component.input.InputText
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

@Preview(showBackground = true)
@Composable
private fun PreviewDecimalInput() {
    val state = remember {
        mutableStateOf(
            InputTextState(
                value = TextFieldValue(0.5.toString()),
            )
        )
    }
    val cgr = remember { CurrencyGroupingVisualTransformationSuffix(
        DecimalFormatSymbols(
            Locale.getDefault()
        ),
        '%',
    ) }

    val decimalFormatSymbolsValueFormatter: DecimalFormatSymbolsValueFormatter<BigDecimal> =
        remember {
            DecimalFormatSymbolsBigDecimalFormatter().apply {
                decimalPrecision = 2
            }
        }

    val decimalFormatSymbols = remember {
        DecimalFormatSymbols(Locale.ENGLISH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage)
            .padding(Dimens.x2)
    ) {
        InputText(
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = cgr,
            state = state.value,
            onValueChange = {
                val r = decimalFormatSymbolsValueFormatter.formatStringValueOrAbort(
                    stringValue = it.text,
                    decimalFormatSymbols = decimalFormatSymbols
                )
                if (r != null) {
                    state.value = state.value.copy(
                        value = it.copy(
                            text = r
                        )
                    )
                }
            },
            maxLines = 1,
            singleLine = true,
        )
    }
}
