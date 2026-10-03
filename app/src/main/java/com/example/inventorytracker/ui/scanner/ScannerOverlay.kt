package com.example.inventorytracker.ui.scanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Overlay composable for barcode scanning UI.
 * Draws semi-transparent background with a clear viewfinder box cutout,
 * animated scanning line, corner brackets, and control buttons.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    boxWidth: Dp = 280.dp,
    boxHeight: Dp = 180.dp,
    boxCornerRadius: Dp = 16.dp,
    isTorchEnabled: Boolean = false,
    instructionText: String = "Align UPC/EAN barcode within the frame",
    onToggleTorch: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val density = LocalDensity.current
    val boxWidthPx = with(density) { boxWidth.toPx() }
    val boxHeightPx = with(density) { boxHeight.toPx() }
    val boxCornerRadiusPx = with(density) { boxCornerRadius.toPx() }

    val primaryColor = MaterialTheme.colorScheme.primary
    val overlayColor = Color.Black.copy(alpha = 0.65f)

    // Animated scanning line value (0f to 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_progress"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Custom canvas for dark background cutout and viewport styling
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = (size.width - boxWidthPx) / 2f
            val top = (size.height - boxHeightPx) / 2.2f
            val right = left + boxWidthPx
            val bottom = top + boxHeightPx

            val rect = Rect(left, top, right, bottom)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = rect,
                        cornerRadius = CornerRadius(boxCornerRadiusPx, boxCornerRadiusPx)
                    )
                )
            }

            // Clip the canvas to draw the overlay outside the box
            clipPath(path = path, clipOp = ClipOp.Difference) {
                drawRect(color = overlayColor)
            }

            // Draw corner brackets
            val bracketLength = 32.dp.toPx()
            val bracketStroke = 4.dp.toPx()

            // Top-left
            drawLine(
                color = primaryColor,
                start = Offset(left, top + bracketLength),
                end = Offset(left, top),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(left, top),
                end = Offset(left + bracketLength, top),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Top-right
            drawLine(
                color = primaryColor,
                start = Offset(right - bracketLength, top),
                end = Offset(right, top),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(right, top),
                end = Offset(right, top + bracketLength),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Bottom-left
            drawLine(
                color = primaryColor,
                start = Offset(left, bottom - bracketLength),
                end = Offset(left, bottom),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(left, bottom),
                end = Offset(left + bracketLength, bottom),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Bottom-right
            drawLine(
                color = primaryColor,
                start = Offset(right - bracketLength, bottom),
                end = Offset(right, bottom),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(right, bottom - bracketLength),
                end = Offset(right, bottom),
                strokeWidth = bracketStroke,
                cap = StrokeCap.Round
            )

            // Animated Laser Scanning Line inside viewport
            val lineY = top + (boxHeightPx * scanLineProgress)
            drawLine(
                color = primaryColor.copy(alpha = 0.85f),
                start = Offset(left + 16.dp.toPx(), lineY),
                end = Offset(right - 16.dp.toPx(), lineY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Top Header Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 40.dp)
                .align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close Scanner"
                )
            }

            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier.align(Alignment.TopEnd),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isTorchEnabled) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.5f),
                    contentColor = if (isTorchEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                )
            ) {
                Icon(
                    imageVector = if (isTorchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = if (isTorchEnabled) "Turn Flash Off" else "Turn Flash On"
                )
            }
        }

        // Bottom Instruction Text
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp, start = 32.dp, end = 32.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.7f),
            contentColor = Color.White
        ) {
            Text(
                text = instructionText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
