package com.augustusmachin.android_bt_kbmouse

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val BINARY_SEARCH_STEP_SP = 0.5f

/**
 * Text that auto-sizes (binary search) to the largest font that fits the
 * available width on a single line. Shared by the on-screen key grids.
 */
@Composable
fun ResponsiveText(
    text: String,
    minSize: TextUnit = 10.sp,
    maxSize: TextUnit = 16.sp,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BoxWithConstraints {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // Convert available width to pixels for measurement
        val maxWidthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }

        // Binary search for largest font size (in sp) that fits within maxWidth and single line
        val minSp = minSize.value
        val maxSp = maxSize.value
        var best = minSp
        var lo = minSp
        var hi = maxSp
        while (lo <= hi) {
            val mid = (lo + hi) / 2f
            val style = TextStyle(fontSize = mid.sp, fontWeight = fontWeight, letterSpacing = letterSpacing)
            val result =
                measurer.measure(
                    AnnotatedString(text),
                    style = style,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
            val fits = result.size.width <= maxWidthPx && result.lineCount <= 1
            if (fits) {
                best = mid
                lo = mid + BINARY_SEARCH_STEP_SP
            } else {
                hi = mid - BINARY_SEARCH_STEP_SP
            }
        }

        Text(
            text = text,
            fontSize = best.sp,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
