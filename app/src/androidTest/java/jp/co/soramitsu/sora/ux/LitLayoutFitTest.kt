package jp.co.soramitsu.sora.ux

import jp.co.soramitsu.ui_core.theme.customTypography
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.lit.LitLayout
import jp.co.soramitsu.ui_core.lit.LitLightRig
import jp.co.soramitsu.ui_core.lit.NameAndFigures
import jp.co.soramitsu.ui_core.lit.PickerAndField
import jp.co.soramitsu.ui_core.lit.litContentWidth
import jp.co.soramitsu.ui_core.lit.litPlate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What every screen is built on: one column of readable width in any window, labels that fit their buttons, and a
 * light that moves what is drawn without moving where it is laid out.
 */
@RunWith(AndroidJUnit4::class)
class LitLayoutFitTest {
    @get:Rule val compose = createComposeRule()

    @After fun lightAtRest() {
        compose.runOnUiThread { LitLightRig.set(0f, 0f) }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.single()
    }

    @Test fun onATabletTheColumnIsItsWidestAndStandsInTheMiddle() {
        compose.setContent {
            // Wider than any phone: the window of a tablet, whatever screen the test runs on. As in the app's
            // hosts, the screen is a child of a plain Box, which does not ask its children to fill it.
            Box(Modifier.requiredWidth(900.dp).height(80.dp).testTag("window")) {
                Box(Modifier.testTag("slot").litContentWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("content"))
                }
            }
        }

        val window = bounds("window")
        val content = bounds("content")
        assertEquals(LitLayout.ContentMaxWidth.value, (content.right - content.left).value, 0.6f)
        assertEquals((900f - LitLayout.ContentMaxWidth.value) / 2f, (content.left - window.left).value, 0.6f)
        // The slot itself spans the window: the column stands in the middle of it, not at its start.
        assertEquals(900f, (bounds("slot").right - bounds("slot").left).value, 0.6f)
    }

    @Test fun aColumnAskedToFillAWideWindowStandsInItsMiddleToo() {
        compose.setContent {
            Box(Modifier.requiredWidth(900.dp).height(80.dp).testTag("window")) {
                Box(Modifier.fillMaxWidth().litContentWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("content"))
                }
            }
        }

        val content = bounds("content")
        assertEquals(LitLayout.ContentMaxWidth.value, (content.right - content.left).value, 0.6f)
        assertEquals((900f - LitLayout.ContentMaxWidth.value) / 2f, (content.left - bounds("window").left).value, 0.6f)
    }

    @Test fun onAPhoneTheColumnIsTheWindow() {
        compose.setContent {
            Box(Modifier.requiredWidth(320.dp).height(80.dp).testTag("window")) {
                Box(Modifier.litContentWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag("content"))
                }
            }
        }

        assertEquals(bounds("window").left.value, bounds("content").left.value, 0.6f)
        assertEquals(320f, (bounds("content").right - bounds("content").left).value, 0.6f)
    }

    @Test fun aLabelIsSetSmallerBeforeItIsCutAndOnlyThenEndsInAnEllipsis() {
        val label = "Review request"
        var width by mutableStateOf<Dp?>(null)
        var ownWidth = 0f
        var leastWidth = 0f
        compose.setContent {
            SoraAppTheme {
                // The label's width in the button's own type, and at 11 sp, measured apart from the button.
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val type = MaterialTheme.customTypography.buttonM
                ownWidth = measurer.measure(label, type).let { it.getLineRight(0) - it.getLineLeft(0) }
                leastWidth = measurer.measure(label, type.copy(fontSize = 11.sp)).let { it.getLineRight(0) - it.getLineLeft(0) }
                FilledButton(
                    modifier = (width?.let { Modifier.width(it) } ?: Modifier).testTag("button"),
                    size = Size.Large,
                    order = Order.PRIMARY,
                    text = label,
                    onClick = {},
                )
            }
        }
        val text = { compose.onNodeWithText(label, useUnmergedTree = true).textLayout() }
        // How wide the label is set. The semantics' layout result is the label's real layout, but it reports the
        // style it was given, not the size it was fitted to: the line's width is what shows the size.
        val inked = { layout: TextLayoutResult -> layout.getLineRight(0) - layout.getLineLeft(0) }

        // With room it is set in the button's own type, exactly, on one line.
        val natural = text()
        assertFalse(natural.hasVisualOverflow)
        assertEquals(1, natural.lineCount)
        val full = inked(natural)
        assertEquals("a label with room is not at the button's own size", ownWidth, full, 1f)
        val button = bounds("button")
        val buttonWidth = button.right - button.left
        val labelWidth = with(compose.density) { natural.size.width.toDp() }
        // Everything but the label, once the label has taken the half of its end spacing it may take (24 of 48 dp).
        val sides = buttonWidth - labelWidth - 24.dp

        // Narrower than the label with all its spacing, but wide enough with half of it: the label at its own size.
        compose.runOnIdle { width = sides + labelWidth + 4.dp }
        val spaced = text()
        assertFalse(spaced.hasVisualOverflow)
        assertEquals("the label shrank although its spacing could give way", full, inked(spaced), 1f)

        // Too narrow even so: the whole label, smaller, but never under 11 sp.
        compose.runOnIdle { width = sides + labelWidth * 0.85f }
        val smaller = text()
        assertFalse("the label is cut although a smaller size fits", smaller.hasVisualOverflow)
        assertFalse(smaller.isLineEllipsized(0))
        assertTrue("the label was not set smaller", inked(smaller) < full - 1f)
        assertTrue("the label went under 11 sp", inked(smaller) >= leastWidth - 1f)

        // Far too narrow for any size it may take: cut at the end with an ellipsis, never mid-letter.
        compose.runOnIdle { width = sides + labelWidth * 0.5f }
        assertTrue(text().isLineEllipsized(0))

        // Room again: back to its own size.
        compose.runOnIdle { width = null }
        assertEquals(full, inked(text()), 1f)
    }

    @Test fun aButtonInARowThatScrollsSidewaysKeepsItsLabel() {
        compose.setContent {
            SoraAppTheme {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FilledButton(size = Size.Small, order = Order.SECONDARY, text = "Send", onClick = {})
                    NeonButton(size = Size.Small, variant = NeonVariant.Secondary, text = "Receive", onClick = {})
                }
            }
        }

        // Offered any width at all, each label takes its own.
        for (label in listOf("Send", "Receive")) {
            val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
            assertTrue("$label has no width", layout.size.width > 0)
            assertFalse(layout.hasVisualOverflow)
        }
    }

    @Test fun aSoftButtonFitsItsLabelTheSameWay() {
        val label = "Create request"
        var width by mutableStateOf<Dp?>(null)
        compose.setContent {
            SoraAppTheme {
                NeonButton(
                    modifier = (width?.let { Modifier.width(it) } ?: Modifier).testTag("button"),
                    size = Size.Large,
                    variant = NeonVariant.Secondary,
                    text = label,
                    onClick = {},
                )
            }
        }
        val text = { compose.onNodeWithText(label, useUnmergedTree = true).textLayout() }
        val natural = text()
        val button = bounds("button")
        val labelWidth = with(compose.density) { natural.size.width.toDp() }
        val sides = button.right - button.left - labelWidth - 24.dp

        compose.runOnIdle { width = sides + labelWidth * 0.85f }

        val smaller = text()
        assertFalse(smaller.hasVisualOverflow)
        assertFalse(smaller.isLineEllipsized(0))
        // The line's width shows the size it was set in (the semantics' result reports the style it was given).
        val inked = { layout: TextLayoutResult -> layout.getLineRight(0) - layout.getLineLeft(0) }
        assertTrue("the label was not set smaller", inked(smaller) < inked(natural) - 1f)
    }

    @Test fun theLightMovesWhatIsDrawnAndNotWhereItIsLaidOutOrTouched() {
        var taps = 0
        // No app theme here: it would put the screen under the phone's own tilt sensor, which pulls the light back
        // to rest while the test looks. The materials draw in their daylight palette without it.
        compose.setContent {
            Box(Modifier.padding(40.dp)) {
                Box(Modifier.size(200.dp, 120.dp).litPlate(radius = 18.dp).testTag("sheet")) {
                    Box(
                        Modifier
                            .padding(16.dp)
                            .size(96.dp, 48.dp)
                            .background(Color(0xFFDA2530))
                            .testTag("action")
                            .clickable { taps++ },
                    )
                }
            }
        }
        compose.waitForIdle()
        val sheet = bounds("sheet")
        val action = bounds("action")
        val atRest = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()

        compose.runOnUiThread { LitLightRig.set(1f, 1f) }
        compose.waitForIdle()

        // Laid out where it was: nothing under a sheet is measured or placed again when the phone tilts.
        assertEquals(sheet, bounds("sheet"))
        assertEquals(action, bounds("action"))
        // Drawn under the moved light, a few dp from where it rests.
        val tilted = compose.onNodeWithTag("sheet").captureToImage().asAndroidBitmap()
        assertFalse("the sheet looks the same under a moved light", atRest.sameAs(tilted))
        // Touched where it is laid out, to its very edge: a touch one pixel inside the corner the picture has moved
        // away from still lands.
        compose.onNodeWithTag("action").performTouchInput { click(Offset(1f, 1f)) }
        compose.onNodeWithTag("action").performClick()
        compose.runOnIdle { assertEquals(2, taps) }
    }

    @Test fun figuresStandBesideAWholeNameAndGoUnderItWhenTheyWouldCutIt() {
        var width by mutableStateOf(360.dp)
        var name by mutableStateOf("XOR")
        compose.setContent {
            SoraAppTheme {
                Box(Modifier.width(width)) {
                    NameAndFigures(
                        modifier = Modifier.fillMaxWidth().testTag("row"),
                        leading = { Box(Modifier.size(40.dp).testTag("picture")) },
                        name = { androidx.compose.material.Text(name, style = ROW_TYPE, maxLines = 1, modifier = Modifier.testTag("name")) },
                        figures = { androidx.compose.material.Text("$12,345.67", style = ROW_TYPE, maxLines = 1, modifier = Modifier.testTag("figures")) },
                    )
                }
            }
        }

        // Room for both: one line, the figures at the row's end, level with the name.
        var row = bounds("row")
        var figures = bounds("figures")
        var nameBounds = bounds("name")
        val figuresWidth = figures.right - figures.left
        assertEquals(row.right.value, figures.right.value, 0.6f)
        assertTrue("the figures are not beside the name", figures.top < nameBounds.bottom)
        assertEquals(52f, (nameBounds.left - row.left).value, 0.6f)

        // A name that would be cut beside them: whole, with the figures under it at the end.
        compose.runOnIdle {
            width = 220.dp
            name = "Savings wallet 2"
        }
        row = bounds("row")
        figures = bounds("figures")
        nameBounds = bounds("name")
        assertFalse("the name was cut", compose.onNodeWithTag("name").textLayout().hasVisualOverflow)
        assertTrue("the figures are not under the name", figures.top >= nameBounds.bottom)
        assertEquals(row.right.value, figures.right.value, 0.6f)
        // Whole: as wide as beside the name.
        assertEquals(figuresWidth.value, (figures.right - figures.left).value, 0.6f)

        // Room again: back beside it.
        compose.runOnIdle { width = 480.dp }
        assertTrue(bounds("figures").top < bounds("name").bottom)
    }

    @Test fun aNameThatFitsItsTwoLinesBesideTheFiguresKeepsThemThere() {
        val twoLines = "Savings for the trip to Kyoto"
        val tooLong = "Savings for the long trip around the world next year"
        var name by mutableStateOf(twoLines)
        compose.setContent {
            SoraAppTheme {
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                Box(Modifier.width(240.dp)) {
                    NameAndFigures(
                        modifier = Modifier.fillMaxWidth(),
                        nameFits = { room, _ ->
                            !measurer.measure(name, ROW_TYPE, maxLines = 2, constraints = androidx.compose.ui.unit.Constraints(maxWidth = room)).hasVisualOverflow
                        },
                        name = { androidx.compose.material.Text(name, style = ROW_TYPE, maxLines = 2, modifier = Modifier.testTag("name")) },
                        figures = { androidx.compose.material.Text("12.5 XOR", style = ROW_TYPE, maxLines = 1, modifier = Modifier.testTag("figures")) },
                    )
                }
            }
        }

        assertTrue("a name that fits in two lines lost its figures beside it", bounds("figures").top < bounds("name").bottom)
        val beside = compose.onNodeWithTag("name").textLayout()
        assertEquals(2, beside.lineCount)
        assertFalse(beside.hasVisualOverflow)

        compose.runOnIdle { name = tooLong }
        assertTrue("a name too long for two lines kept the figures beside it", bounds("figures").top >= bounds("name").bottom)
    }

    @Test fun anAmountTooNarrowBesideItsTokenTakesALineOfItsOwn() {
        var width by mutableStateOf(320.dp)
        compose.setContent {
            SoraAppTheme {
                Box(Modifier.width(width)) {
                    PickerAndField(
                        fieldMin = 140.dp,
                        modifier = Modifier.fillMaxWidth().testTag("line"),
                        picker = { Box(Modifier.size(130.dp, 48.dp).testTag("picker")) },
                        field = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("field")) },
                    )
                }
            }
        }

        // 320 dp: 182 dp beside the token, more than the least an amount gets.
        var field = bounds("field")
        assertEquals(320f - 130f - 8f, (field.right - field.left).value, 0.6f)
        assertEquals(bounds("line").right.value, field.right.value, 0.6f)
        assertTrue(field.top < bounds("picker").bottom)

        // 260 dp: 122 dp would be too few digits, so the amount stands under the token, as wide as the line.
        compose.runOnIdle { width = 260.dp }
        field = bounds("field")
        assertEquals(260f, (field.right - field.left).value, 0.6f)
        assertTrue(field.top >= bounds("picker").bottom)
    }

    @Test fun figuresOnALineOfTheirOwnMayTakeTheWholeLine() {
        compose.setContent {
            SoraAppTheme {
                Box(Modifier.width(240.dp)) {
                    NameAndFigures(
                        modifier = Modifier.fillMaxWidth().testTag("row"),
                        leading = { Box(Modifier.size(40.dp)) },
                        name = { androidx.compose.material.Text("XSTUSD", style = ROW_TYPE, maxLines = 1, modifier = Modifier.testTag("name")) },
                        // A figure 210 dp wide: more than the name's column (240 - 52 dp), less than the row.
                        figures = { Box(Modifier.size(210.dp, 20.dp).testTag("figures")) },
                    )
                }
            }
        }

        // Whole, under the name, out to the row's end.
        val figures = bounds("figures")
        assertEquals(210f, (figures.right - figures.left).value, 0.6f)
        assertTrue(figures.top >= bounds("name").bottom)
        assertEquals(bounds("row").right.value, bounds("figures").right.value, 0.6f)
    }

    @Test fun withNothingChosenThePickerTakesTheWholeLine() {
        compose.setContent {
            SoraAppTheme {
                Box(Modifier.width(320.dp)) {
                    PickerAndField(
                        fieldMin = 140.dp,
                        modifier = Modifier.fillMaxWidth(),
                        picker = { Box(Modifier.height(48.dp).testTag("picker")) },
                        field = {},
                    )
                }
            }
        }

        val picker = bounds("picker")
        assertEquals(320f, (picker.right - picker.left).value, 0.6f)
    }

    @Test fun aSegmentLabelTooLongForOneLineWrapsAtItsFirstLayout() {
        val label = "Automatic theme from the system"
        compose.setContent {
            SoraAppTheme {
                Box(Modifier.width(72.dp)) {
                    jp.co.soramitsu.ui_core.component.text.FittedLabel(label, ROW_TYPE, Modifier.fillMaxWidth())
                }
            }
        }

        val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
        assertFalse("the label was cut", layout.hasVisualOverflow)
        assertTrue("the label did not wrap", layout.lineCount > 1)
    }
}

private val ROW_TYPE = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 20.sp)
