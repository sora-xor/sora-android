package jp.co.soramitsu.ui_core.component.button

import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.extensions.ripple
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.opacity

@Immutable
object ButtonColors {

    @Composable
    fun filledPolkaswapButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return ButtonDefaults.buttonColors(
            backgroundColor = Color(0xffff0066),
            contentColor = Color(0xffffffff),
            disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                MaterialTheme.opacity.actionBgDisabled
            ),
            disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                MaterialTheme.opacity.actionFgDisabled
            )
        )
    }

    @Composable
    fun filledButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return when (order) {
            Order.PRIMARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentPrimary,
                    contentColor = MaterialTheme.customColors.fgInverted,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.SECONDARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentSecondary,
                    contentColor = MaterialTheme.customColors.fgInverted,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.TERTIARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentTertiary,
                    contentColor = MaterialTheme.customColors.fgInverted,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }
        }
    }

    @Composable
    fun outlineButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return when (order) {
            Order.PRIMARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentPrimary,
                    disabledBackgroundColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.SECONDARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentSecondary,
                    disabledBackgroundColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.TERTIARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentTertiary,
                    disabledBackgroundColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }
        }
    }

    @Composable
    fun textButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return when (order) {
            Order.PRIMARY -> {
                ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentPrimary,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.SECONDARY -> {
                ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentSecondary,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.TERTIARY -> {
                ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MaterialTheme.customColors.accentTertiary,
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }
        }
    }

    @Composable
    fun tonalButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return when (order) {
            Order.PRIMARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentPrimaryContainer,
                    contentColor = MaterialTheme.customColors.accentPrimary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.SECONDARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentSecondaryContainer,
                    contentColor = MaterialTheme.customColors.accentSecondary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.TERTIARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.accentTertiaryContainer,
                    contentColor = MaterialTheme.customColors.accentTertiary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }
        }
    }

    @Composable
    fun bleachedButtonColors(order: Order): androidx.compose.material.ButtonColors {
        return when (order) {
            Order.PRIMARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.bgSurface,
                    contentColor = MaterialTheme.customColors.accentPrimary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.SECONDARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.bgSurface,
                    contentColor = MaterialTheme.customColors.accentSecondary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }

            Order.TERTIARY -> {
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.customColors.bgSurface,
                    contentColor = MaterialTheme.customColors.accentTertiary,
                    disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionBgDisabled
                    ),
                    disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                        MaterialTheme.opacity.actionFgDisabled
                    )
                )
            }
        }
    }

    @Composable
    fun rippleColor(order: Order): Color {
        return when (order) {
            Order.PRIMARY -> {
                MaterialTheme.customColors.accentPrimary.ripple()
            }

            Order.SECONDARY -> {
                MaterialTheme.customColors.accentSecondary.ripple()
            }

            Order.TERTIARY -> {
                MaterialTheme.customColors.accentTertiary.ripple()
            }
        }
    }
}
