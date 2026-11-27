package jp.co.soramitsu.ui_core.component.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
interface InputTextColors {

    @Composable
    fun borderColor(state: InputTextState, focused: Boolean): Color

    @Composable
    fun descriptionTextColor(state: InputTextState): Color
}

@Immutable
private class DefaultInputTextColors(
    private val borderColor: Color,
    private val disabledBorderColor: Color,
    private val focusedBorderColor: Color,
    private val unfocusedBorderColor: Color,
    private val errorBorderColor: Color,
    private val successBorderColor: Color,
    private val descriptionTextColor: Color,
    private val disabledDescriptionTextColor: Color,
    private val errorDescriptionTextColor: Color,
    private val successDescriptionTextColor: Color,
) : InputTextColors {

    @Composable
    override fun borderColor(state: InputTextState, focused: Boolean): Color {
        val targetValue = when {
            !state.enabled -> disabledBorderColor
            !focused -> unfocusedBorderColor
            state.error -> errorBorderColor
            state.success -> successBorderColor
            focused -> focusedBorderColor
            else -> borderColor
        }

        return targetValue
    }

    @Composable
    override fun descriptionTextColor(state: InputTextState): Color {
        val targetValue = when {
            !state.enabled -> disabledDescriptionTextColor
            state.error -> errorDescriptionTextColor
            state.success -> successDescriptionTextColor
            else -> descriptionTextColor
        }

        return targetValue
    }
}

@Immutable
object InputTextDefaults {

    @Composable
    fun inputTextBorderColors(
        borderColor: Color,
        disabledBorderColor: Color,
        focusedBorderColor: Color,
        unfocusedBorderColor: Color = Color.Transparent,
        errorBorderColor: Color = Color.Transparent,
        successBorderColor: Color = Color.Transparent,
        descriptionTextColor: Color = Color.Transparent,
        disabledDescriptionTextColor: Color = Color.Transparent,
        errorDescriptionTextColor: Color = Color.Transparent,
        successDescriptionTextColor: Color = Color.Transparent,
    ): InputTextColors {
        return DefaultInputTextColors(
            borderColor = borderColor,
            disabledBorderColor = disabledBorderColor,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            errorBorderColor = errorBorderColor,
            successBorderColor = successBorderColor,
            descriptionTextColor = descriptionTextColor,
            disabledDescriptionTextColor = disabledDescriptionTextColor,
            errorDescriptionTextColor = errorDescriptionTextColor,
            successDescriptionTextColor = successDescriptionTextColor
        )
    }
}
