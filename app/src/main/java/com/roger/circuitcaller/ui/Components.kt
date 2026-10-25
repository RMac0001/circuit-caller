package com.roger.circuitcaller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.roger.circuitcaller.timer.Segment
import com.roger.circuitcaller.timer.SegmentType
import com.roger.circuitcaller.ui.theme.Line
import com.roger.circuitcaller.ui.theme.Prep
import com.roger.circuitcaller.ui.theme.Rest
import com.roger.circuitcaller.ui.theme.RingTrack
import com.roger.circuitcaller.ui.theme.Surface
import com.roger.circuitcaller.ui.theme.Surface2
import com.roger.circuitcaller.ui.theme.TextDim
import com.roger.circuitcaller.ui.theme.Work

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextDim,
        modifier = modifier
    )
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
    ) { content() }
}

@Composable
fun StepperBox(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardSurface(modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Eyebrow(label)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                StepButton("–", onDecrement)
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                StepButton("+", onIncrement)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    CardSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Work,
                    checkedTrackColor = Work.copy(alpha = 0.30f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = Surface2
                )
            )
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit
) {
    CardSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
            }
            trailing()
        }
    }
}

fun colorForSegment(type: SegmentType): Color = when (type) {
    SegmentType.WORK -> Work
    SegmentType.REST -> Rest
    SegmentType.PREP -> Prep
}

/**
 * The whole session drawn as proportional blocks. This is the piece that makes the app
 * legible mid-set: one glance shows what's done, what's running, and how much is left.
 */
@Composable
fun SegmentBar(
    segments: List<Segment>,
    currentIndex: Int,
    currentFractionRemaining: Float,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) return
    val total = segments.sumOf { it.durationSeconds }.toFloat().coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(7.dp)) {
        val gap = 3.dp.toPx()
        val available = size.width - gap * (segments.size - 1)
        var x = 0f
        segments.forEachIndexed { index, seg ->
            val w = (seg.durationSeconds / total) * available
            val trackColor = if (index < currentIndex) Line else RingTrack
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(w.coerceAtLeast(1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            )
            val fillFraction = when {
                index < currentIndex -> 1f
                index == currentIndex -> (1f - currentFractionRemaining).coerceIn(0f, 1f)
                else -> 0f
            }
            if (fillFraction > 0f) {
                drawRoundRect(
                    color = colorForSegment(seg.type)
                        .copy(alpha = if (index < currentIndex) 0.35f else 1f),
                    topLeft = Offset(x, 0f),
                    size = Size((w * fillFraction).coerceAtLeast(1f), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
            x += w + gap
        }
    }
}

@Composable
fun ProgressRing(
    fractionRemaining: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = RingTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fractionRemaining.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = Surface,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    border: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, Line, CircleShape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
fun VSpace(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))
