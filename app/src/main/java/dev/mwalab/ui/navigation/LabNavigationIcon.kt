package dev.mwalab.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Small code-native icons. The visible navigation label owns accessibility semantics. */
@Composable
fun LabNavigationIcon(destination: AppDestination) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke, StrokeCap.Round)
        when (destination) {
            AppDestination.HOME -> {
                line(.12f, .45f, .5f, .13f); line(.5f, .13f, .88f, .45f)
                line(.22f, .43f, .22f, .86f); line(.78f, .43f, .78f, .86f)
                line(.22f, .86f, .78f, .86f); line(.43f, .86f, .43f, .62f); line(.57f, .62f, .57f, .86f)
            }
            AppDestination.SESSIONS -> {
                listOf(.25f, .50f, .75f).forEach { y ->
                    drawCircle(color, stroke * .7f, Offset(w * .17f, h * y))
                    line(.34f, y, .87f, y)
                }
            }
            AppDestination.FAULT_LAB -> {
                val triangle = Path().apply {
                    moveTo(w * .5f, h * .12f); lineTo(w * .9f, h * .86f)
                    lineTo(w * .1f, h * .86f); close()
                }
                drawPath(triangle, color, style = Stroke(stroke))
                line(.5f, .39f, .5f, .61f)
                drawCircle(color, stroke * .5f, Offset(w * .5f, h * .73f))
            }
            AppDestination.LAB_IDENTITY -> {
                drawCircle(color, w * .14f, Offset(w * .5f, h * .32f), style = Stroke(stroke))
                val shoulders = Path().apply {
                    moveTo(w * .18f, h * .86f)
                    lineTo(w * .18f, h * .74f)
                    quadraticTo(w * .5f, h * .49f, w * .82f, h * .74f)
                    lineTo(w * .82f, h * .86f)
                }
                drawPath(shoulders, color, style = Stroke(stroke))
            }
            AppDestination.SETTINGS -> {
                line(.15f, .28f, .85f, .28f); line(.15f, .72f, .85f, .72f)
                drawCircle(color, w * .10f, Offset(w * .37f, h * .28f), style = Stroke(stroke))
                drawCircle(color, w * .10f, Offset(w * .65f, h * .72f), style = Stroke(stroke))
            }
            AppDestination.SESSION_DETAIL -> Unit
        }
    }
}
