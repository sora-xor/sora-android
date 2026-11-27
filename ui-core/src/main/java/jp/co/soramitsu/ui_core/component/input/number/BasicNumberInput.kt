package jp.co.soramitsu.ui_core.component.input.number

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import jp.co.soramitsu.ui_core.modifier.applyIf
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

private enum class BasicNumberInputSource {
    KEYBOARD_CHANGE, OUTSIDE_CHANGE
}

enum class DefaultCursorPosition {
    START, END
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalCoroutinesApi::class)
@Composable
fun BasicNumberInput(
    modifier: Modifier,
    initial: BigDecimal? = null,
    textStyle: TextStyle,
    contentPadding: PaddingValues =
        remember {
            PaddingValues(0.dp)
        },
    enabled: Boolean,
    precision: Int = 2,
    defaultCursorPosition: DefaultCursorPosition = DefaultCursorPosition.END,
    decimalFormatSymbols: DecimalFormatSymbols =
        remember(Locale.getDefault()) {
            DecimalFormatSymbols(
                Locale.getDefault()
            )
        },
    visualTransformation: VisualTransformation =
        remember {
            CurrencyGroupingVisualTransformation(
                decimalFormatSymbols = decimalFormatSymbols
            )
        },
    indicatorThickness: Dp = 0.dp,
    indicatorEnabled: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
    cursorColor: Color = MaterialTheme.customColors.fgPrimary,
    textFieldColors: TextFieldColors = TextFieldDefaults.textFieldColors(
        textColor = MaterialTheme.customColors.fgPrimary,
        placeholderColor = MaterialTheme.customColors.fgSecondary,
        cursorColor = MaterialTheme.customColors.fgPrimary,
        focusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
        unfocusedIndicatorColor = MaterialTheme.customColors.fgSecondary,
        backgroundColor = Color.Transparent
    ),
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onKeyboardDone: (() -> Unit)? = null,
    onValueChanged: (BigDecimal) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val decimalSeparator = remember(decimalFormatSymbols) {
        decimalFormatSymbols.decimalSeparator
    }

    val minusSign = remember(decimalFormatSymbols) {
        decimalFormatSymbols.minusSign
    }

    val decimalFormatSymbolsValueFormatter: DecimalFormatSymbolsValueFormatter<BigDecimal> =
        remember(precision) {
            DecimalFormatSymbolsBigDecimalFormatter().apply {
                decimalPrecision = precision
            }
        }

    /*
        CurrentFieldState holds two important pieces of information:
        1) raw TextFieldValue that SHOULD NOT be set to InputField
        2) source of that changes

        So, if user will paste, or enter some data that does not fit
        our constraints on Input texts, then it will saved here,
        AND handled(filtered) in specific snapshot flow (look below)
        before actually being set as InputField value for user observation
    */
    var currentFieldState by remember {
        mutableStateOf(
            value = TextFieldValue(
                text = DEFAULT_INPUT_VALUE_AS_STRING,
                selection = TextRange(DEFAULT_INPUT_VALUE_LENGTH)
            ) to BasicNumberInputSource.KEYBOARD_CHANGE
        )
    }

    /*
        NewFieldValue also a holder of TextFieldValue (similar to CurrentFieldState)
        but now it's filtered out data that can safely be shown to user

        Thus, exactly this field is used in BasicTextField (meaning,
        it is actually shown to user)
     */
    var newFieldValue by remember {
        mutableStateOf(
            value =  TextFieldValue(
                text = DEFAULT_INPUT_VALUE_AS_STRING,
                selection = TextRange(DEFAULT_INPUT_VALUE_LENGTH)
            )
        )
    }

    LaunchedEffect(initial) {
        /*
            Omission of inputs that are now shown on user screen

            Important: comparison is done on BigDecimal type for the following reason:
            - when user inputs "10." of string type it gets converted to 10
            of bigDecimal type (without the dot), then passed to client via
            onValueChanged lambda, and after returned as initial parameter
            So, if initial will then be compared to currently displayed text, we'll get
            this: "10"(initialAsString) == "10."(currently displayed text),
            which should be true, but will be false, so both values should
            firstly get converted to the same format via convertFromString method
            and then get compared to obtain correct results
         */
        val isInputAlreadyShown =
            decimalFormatSymbolsValueFormatter.convertFromString(
                newFieldValue.text,
                decimalFormatSymbols
            ).equals(initial)

        if (isInputAlreadyShown)
            return@LaunchedEffect

        /* Now, we can convert initial to string, if it truly is a new value to be displayed */
        val initialAsString =
            decimalFormatSymbolsValueFormatter.convertToString(
                value = initial,
                decimalFormatSymbols = decimalFormatSymbols
            )

        /*
            When a new input value comes, it's hard to understand
            in which position cursor position must be placed because
            length of the new input can be much greater or much smaller
            than previous input value; so, we want to control it by setting to 0 (that is Start)
            or to length of the new input (that is the end of the new input)
        */
        val textRangeSelection = if(defaultCursorPosition == DefaultCursorPosition.END)
            TextRange(index = initialAsString.length) else TextRange(index = 0)

        currentFieldState = TextFieldValue(
            text = initialAsString,
            selection = textRangeSelection
        ) to BasicNumberInputSource.OUTSIDE_CHANGE
    }

    /* Lifecycle of composable that will tell when composable is on screen and off of it */
    val lifecycleOwner = LocalLifecycleOwner.current

    /* Local coroutineScope used to start jobs and cancel them based on lifecycle events */
    val coroutineScope = rememberCoroutineScope()

    /* Using disposable effect to cancel snapshot flow when BasicNumberInput is not on screen */
    DisposableEffect(lifecycleOwner) {
        val jobsToDisposeOf = mutableListOf<Job>()

        val plainZeroCase = "0"
        val zeroWithDecSeparatorCase = "0$decimalSeparator"
        val negativeZeroWithDecSeparatorEdgeCase = "${minusSign}0$decimalSeparator"

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                jobsToDisposeOf += snapshotFlow { currentFieldState }
                    .mapLatest { (currentFieldValue, inputSource) ->
                        val formattedValue = decimalFormatSymbolsValueFormatter.formatStringValueOrAbort(
                            stringValue = currentFieldValue.text,
                            decimalFormatSymbols = decimalFormatSymbols
                        ) ?: return@mapLatest null

                        return@mapLatest formattedValue to inputSource
                    }.filterNotNull().onEach { (newValueString, _) ->
                        val shouldUserBeForcedToAppendOnlyInTheEnd =
                            newValueString.isEmpty() ||
                            newValueString == plainZeroCase ||
                            newValueString == zeroWithDecSeparatorCase ||
                            newValueString == negativeZeroWithDecSeparatorEdgeCase

                        val selection = if (shouldUserBeForcedToAppendOnlyInTheEnd)
                            TextRange(newValueString.length) else
                                currentFieldState.first.selection

                        TextFieldValue(
                            text = newValueString,
                            selection = selection
                        ).apply { newFieldValue = this }
                    }.distinctUntilChanged().filter {  (_, inputSource) ->
                        /*
                            We want to update client only if the change has come from IME,
                            any other change most probably is already known to client,
                            so by updating client with this repeated information
                            we will only recompose this view one more time
                            and execute so value
                         */
                        inputSource === BasicNumberInputSource.KEYBOARD_CHANGE
                    }.map { (newValueString, _) ->
                        /*
                            We want to omit sending client the same info that
                            he already supplied us with; so we'll be using distinctUntilChanged()
                            on formatted string to obtain only necessary changes

                            Possible case: selection has been changed by user
                            but the value has not been changed -> we should omit client update
                         */
                        return@map newValueString
                    }.onEach { newValueString ->
                        /*
                            Now, given a NEW transformed string value we need to convert it back
                            to BigDecimal before updating a client
                         */
                        val newValueBigDecimal =
                            decimalFormatSymbolsValueFormatter.convertFromString(
                                stringValue = newValueString,
                                decimalFormatSymbols = decimalFormatSymbols
                            )

                        onValueChanged.invoke(newValueBigDecimal)
                    }.launchIn(coroutineScope)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                jobsToDisposeOf.forEach { it.cancel() }
                jobsToDisposeOf.clear()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /* Using remember to prevent creation of new modifiers between recompositions */
    val reusableModifier = remember(
        focusRequester,
        onFocusChanged,
        indicatorEnabled,
        indicatorThickness,
        textFieldColors
    ) {
        modifier
            .applyIf(focusRequester != null) {
                focusRequester(focusRequester!!)
            }
            .applyIf(onFocusChanged != null) {
                onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
            }
            .indicatorLine(
                enabled = indicatorEnabled,
                isError = false,
                interactionSource,
                textFieldColors,
                focusedIndicatorLineThickness = indicatorThickness,
                unfocusedIndicatorLineThickness = indicatorThickness,
            )
    }

    /* Using remember to prevent creation of new lambdas between recompositions */
    val reusableOnValueChange: (TextFieldValue) -> Unit = remember {
        { currentFieldState = it to BasicNumberInputSource.KEYBOARD_CHANGE }
    }

    /* Using remember to prevent creation of new objects between recompositions */
    val reusableKeyboardOptions = remember {
        KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Decimal,
        )
    }

    /* Using remember to prevent creation of new objects between recompositions */
    val reusableKeyboardActions = remember(onKeyboardDone) {
        KeyboardActions(
            onDone = { onKeyboardDone?.invoke() },
        )
    }

    /* Using remember to prevent creation of new objects between recompositions */
    val reusableCursorColor = remember(cursorColor) {
        SolidColor(cursorColor)
    }

    BasicTextField(
        value = newFieldValue,
        onValueChange = reusableOnValueChange,
        modifier = reusableModifier,
        enabled = enabled,
        singleLine = true,
        maxLines = 1,
        textStyle = textStyle,
        keyboardOptions = reusableKeyboardOptions,
        keyboardActions = reusableKeyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = reusableCursorColor,
    ) { innerTextField: @Composable () -> Unit ->
        TextFieldDefaults.TextFieldDecorationBox(
            value = newFieldValue.text,
            visualTransformation = visualTransformation,
            innerTextField = innerTextField,
            placeholder = placeholder,
            singleLine = true,
            enabled = enabled,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            colors = textFieldColors,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xccdd23)
@Composable
private fun PreviewBasicNumberInput() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        BasicNumberInput(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
//            initial = BigDecimal.valueOf(0.5),
            initial = null,
            enabled = true,
            onValueChanged = {

            },
            decimalFormatSymbols = DecimalFormatSymbols(Locale.getDefault()),
            textStyle = MaterialTheme.customTypography.textL,
            placeholder = {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    text = "0",
                    style = MaterialTheme.customTypography.displayS,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.customColors.fgSecondary,
                )
            },
        )
    }
}
