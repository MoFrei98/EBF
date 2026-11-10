package com.example.ebf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.ebf.ui.theme.LocalDarkTheme
import com.example.ebf.ui.theme.LocalReaderColors
import com.example.ebf.ui.theme.ReaderColors
import kotlin.math.ceil

private const val MAX_SHEETS = 3

/**
 * Zeichnet eine Seite als Blatt Papier.
 *
 * - Schatten unter dem Blatt
 * - Blattkanten links (schon gelesen) und rechts (noch zu lesen), je nach Fortschritt
 * - Schattierung am Bundsteg (linker Rand), wie bei einem aufgeschlagenen Buch
 * - [turnShadow]: Schatten, den die Seite beim Umblättern auf die Seite darunter wirft (0..1)
 * - [dim]: Abdunkeln, solange die Seite noch von der umgeblätterten Seite bedeckt ist (0..1)
 */
@Composable
internal fun Paper(
    readFraction: Float,
    turnShadow: () -> Float,
    dim: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalReaderColors.current
    val spineAlpha = if (LocalDarkTheme.current) 0.35f else 0.13f
    val leftSheets = ceil(MAX_SHEETS * readFraction).toInt().coerceIn(0, MAX_SHEETS)
    val rightSheets = ceil(MAX_SHEETS * (1f - readFraction)).toInt().coerceIn(0, MAX_SHEETS)

    Box(
        modifier
            .drawBehind {
                drawSheetEdges(colors, leftSheets, rightSheets)
                val shadow = turnShadow()
                if (shadow > 0f) drawTurnShadow(colors.shade, shadow)
            }
            .shadow(3.dp, RectangleShape)
            .background(colors.paper)
            .drawWithContent {
                drawContent()
                drawSpineShade(colors.shade, spineAlpha)
                val d = dim()
                if (d > 0f) drawRect(colors.shade, alpha = 0.22f * d)
            },
        content = content,
    )
}

private fun DrawScope.drawSheetEdges(colors: ReaderColors, left: Int, right: Int) {
    val step = 2.dp.toPx()
    val inset = 1.dp.toPx()
    val line = 0.6.dp.toPx()
    fun sheet(i: Int, direction: Float) {
        val dx = i * step * direction
        val dy = i * inset
        drawRect(colors.paperEdge, topLeft = Offset(dx, dy), size = Size(size.width, size.height - 2 * dy))
        val x = if (direction > 0) size.width + dx - line / 2 else dx + line / 2
        drawLine(
            color = colors.shade.copy(alpha = 0.18f),
            start = Offset(x, dy),
            end = Offset(x, size.height - dy),
            strokeWidth = line,
        )
    }
    // Von außen nach innen zeichnen, damit nähere Blätter die äußeren überdecken.
    for (i in right downTo 1) sheet(i, direction = 1f)
    for (i in left downTo 1) sheet(i, direction = -1f)
}

private fun DrawScope.drawTurnShadow(shade: Color, strength: Float) {
    val width = 28.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to shade.copy(alpha = 0.30f * strength),
            1f to Color.Transparent,
            startX = size.width,
            endX = size.width + width,
        ),
        topLeft = Offset(size.width, 0f),
        size = Size(width, size.height),
    )
}

private fun DrawScope.drawSpineShade(shade: Color, alpha: Float) {
    val width = size.width * 0.045f
    drawRect(
        brush = Brush.horizontalGradient(
            0f to shade.copy(alpha = alpha),
            0.35f to shade.copy(alpha = alpha * 0.35f),
            1f to Color.Transparent,
            startX = 0f,
            endX = width,
        ),
        size = Size(width, size.height),
    )
}
