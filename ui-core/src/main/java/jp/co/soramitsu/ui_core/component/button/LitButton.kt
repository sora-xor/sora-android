package jp.co.soramitsu.ui_core.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.litCutGlass
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litPress
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

/** What a button's face is made of: [glass] is a slab of cut glass of that colour, null a soft raised pad. */
internal class LitButtonFace(val glass: Color?, val content: Color, val disabledFill: Color, val disabledContent: Color)

/**
 * A button on a lit face. Cut glass is set down on the surface when pressed; a pad is let into it. The face has the
 * button's own size; the touch target round it is at least 48 dp. Cut glass is a slab with small corners, and so is
 * a pad; a round button stays round.
 */
@Composable
internal fun LitButton(
    modifier: Modifier,
    shape: Shape,
    size: Dp,
    face: LitButtonFace,
    enabled: Boolean,
    text: String?,
    leftIcon: Painter?,
    rightIcon: Painter?,
    maxLines: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val round = shape == CircleShape
    val slab = if (round) CircleShape else RoundedCornerShape(Lit.ControlRadius)
    val radius = if (round) size else Lit.ControlRadius
    val material = when {
        !enabled -> Modifier.background(face.disabledFill, slab)
        face.glass != null -> Modifier.litPress(pressed, scale = 1f).litCutGlass(face.glass, pressed, radius)
        else -> Modifier.litPress(pressed).litPad(radius, elevation = 8.dp, pressed = pressed)
    }
    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize()
            .then(if (round) Modifier.requiredSizeIn(minWidth = size, minHeight = size) else Modifier.requiredHeightIn(min = size)),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) face.content else face.disabledContent,
            LocalContentAlpha provides 1f,
        ) {
            Row(
                modifier = material.then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.customColors.accentSecondary, slab) else Modifier,
                ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leftIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(
                            start = text?.let { Dimens.x2 } ?: 0.dp,
                            end = text?.let { Dimens.x1 } ?: 0.dp,
                        ),
                        painter = painter,
                        contentDescription = null,
                    )
                }
                text?.let {
                    ButtonLabel(
                        text = text,
                        // The label takes what the icons leave, so a long one is set smaller rather than cut.
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(vertical = Dimens.x1)
                            .buttonLabelEnds(
                                start = leftIcon?.let { 0.dp } ?: Dimens.x3,
                                end = rightIcon?.let { 0.dp } ?: Dimens.x3,
                            ),
                        maxLines = maxLines,
                    )
                }
                rightIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(
                            start = text?.let { Dimens.x1 } ?: 0.dp,
                            end = text?.let { Dimens.x2 } ?: 0.dp,
                        ),
                        painter = painter,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
