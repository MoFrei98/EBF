package com.example.ebf.ui.reader

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 3D-Umblättern: Die Seite biegt sich wie ein Blatt Papier um den Buchrücken (linker Rand).
 *
 * Die Seite wird in senkrechte Streifen zerlegt. Jeder Streifen bekommt einen eigenen
 * Drehwinkel, wobei der freie Rand vorauseilt – dadurch wölbt sich die Seite. Die
 * Streifen werden perspektivisch projiziert, je nach Winkel schattiert und mit
 * [android.graphics.Canvas.drawBitmapMesh] gezeichnet. Die Rückseite erscheint als Papier.
 */
internal object PageCurl {
    /** Anzahl der Streifen. Mehr Streifen = rundere Biegung. */
    const val STRIPS = 36

    /** Wie weit der freie Rand dem Buchrücken vorauseilt (0 = starre Drehung). */
    private const val CURL_SPREAD = 0.7f

    /** Abstand der gedachten Kamera in Seitenbreiten (kleiner = stärkere Perspektive). */
    private const val CAMERA_DISTANCE = 3.2f

    /** Ergebnis der Geometrie: projizierte Spalten und Winkel je Streifen. */
    class Projection(val x: FloatArray, val top: FloatArray, val bottom: FloatArray, val angles: FloatArray)

    /**
     * Berechnet die gebogene Seite.
     *
     * @param progress 0 = Seite liegt flach, 1 = Seite ist ganz nach links umgeblättert
     */
    fun project(width: Float, height: Float, progress: Float, strips: Int = STRIPS): Projection {
        val step = width / strips
        val xs = FloatArray(strips + 1)
        val zs = FloatArray(strips + 1)
        val angles = FloatArray(strips)
        var x = 0f
        var z = 0f
        for (i in 0 until strips) {
            // Position entlang der Seite: 0 = Buchrücken, 1 = freier Rand.
            val s = (i + 0.5f) / strips
            val t = (progress * (1f + CURL_SPREAD) - CURL_SPREAD * (1f - s)).coerceIn(0f, 1f)
            val theta = (PI * (t * t * (3f - 2f * t))).toFloat()
            angles[i] = theta
            x += step * cos(theta)
            z += step * sin(theta)
            xs[i + 1] = x
            zs[i + 1] = z
        }

        // Perspektive: Was näher an der Kamera ist (größeres z), wird größer.
        val camera = width * CAMERA_DISTANCE
        val cx = width / 2f
        val cy = height / 2f
        val px = FloatArray(strips + 1)
        val top = FloatArray(strips + 1)
        val bottom = FloatArray(strips + 1)
        for (j in 0..strips) {
            val f = camera / (camera - zs[j])
            px[j] = cx + (xs[j] - cx) * f
            top[j] = cy - cy * f
            bottom[j] = cy + (height - cy) * f
        }
        return Projection(px, top, bottom, angles)
    }

    /** Helligkeit der Vorderseite: frontal = hell, schräg = dunkler. */
    fun frontLight(angle: Float): Float = 0.5f + 0.5f * max(0f, cos(angle))

    /** Helligkeit der Rückseite. */
    fun backLight(angle: Float): Float = 0.74f + 0.26f * abs(cos(angle))
}

private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

/**
 * Zeichnet die umblätternde Seite in die Fläche dieser DrawScope (Größe = Seite).
 * Die Seite darf beim Umblättern über die Fläche hinausragen.
 */
internal fun DrawScope.drawCurledPage(
    progress: Float,
    bitmap: ImageBitmap?,
    colorFilter: ColorFilter?,
    paperColor: Color,
    shadowColor: Color,
) {
    val w = size.width
    val h = size.height
    val n = PageCurl.STRIPS
    val p = PageCurl.project(w, h, progress.coerceIn(0f, 1f), n)

    // 1. Weicher Schatten auf der Seite darunter, rechts neben der angehobenen Seite.
    val lift = sin(PI * progress).toFloat().coerceAtLeast(0f)
    val rightmost = p.x.max()
    if (lift > 0.01f && rightmost < w) {
        val shadowWidth = 56.dp.toPx() * lift
        drawRect(
            brush = Brush.horizontalGradient(
                0f to shadowColor.copy(alpha = 0.38f * lift),
                1f to Color.Transparent,
                startX = rightmost,
                endX = rightmost + shadowWidth,
            ),
            topLeft = Offset(rightmost, 0f),
            size = Size(shadowWidth, h),
        )
    }

    // 2. Vorderseite: Bild auf das gebogene Gitter legen, je Spalte schattiert.
    if (bitmap != null) {
        val verts = FloatArray((n + 1) * 4)
        val colors = IntArray((n + 1) * 2)
        val bottomRow = (n + 1) * 2
        for (j in 0..n) {
            verts[j * 2] = p.x[j]
            verts[j * 2 + 1] = p.top[j]
            verts[bottomRow + j * 2] = p.x[j]
            verts[bottomRow + j * 2 + 1] = p.bottom[j]
            val angle = when (j) {
                0 -> p.angles[0]
                n -> p.angles[n - 1]
                else -> (p.angles[j - 1] + p.angles[j]) / 2f
            }
            val light = PageCurl.frontLight(angle)
            val shade = Color(light, light, light).toArgb()
            colors[j] = shade
            colors[n + 1 + j] = shade
        }
        meshPaint.colorFilter = colorFilter?.asAndroidColorFilter()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmapMesh(bitmap.asAndroidBitmap(), n, 1, verts, 0, colors, 0, meshPaint)
        }
    }

    // 3. Streifen, die zur Kamera hin zeigen, aber noch kein Bild haben, und die Rückseite.
    val path = Path()
    val overlap = 0.75f // gegen feine Lücken zwischen den Streifen
    for (i in 0 until n) {
        val backSide = p.x[i + 1] < p.x[i]
        if (!backSide && bitmap != null) continue
        val light = if (backSide) PageCurl.backLight(p.angles[i]) else PageCurl.frontLight(p.angles[i])
        val dir = if (backSide) -overlap else overlap
        path.reset()
        path.moveTo(p.x[i], p.top[i])
        path.lineTo(p.x[i + 1] + dir, p.top[i + 1])
        path.lineTo(p.x[i + 1] + dir, p.bottom[i + 1])
        path.lineTo(p.x[i], p.bottom[i])
        path.close()
        drawPath(path, paperColor.shaded(light))
    }

    // 4. Feine Kante am freien Rand, damit sich die Seite vom Hintergrund abhebt.
    drawLine(
        color = shadowColor.copy(alpha = 0.18f * lift),
        start = Offset(p.x[n], p.top[n]),
        end = Offset(p.x[n], p.bottom[n]),
        strokeWidth = 1.dp.toPx(),
    )
}

private fun Color.shaded(light: Float) = Color(red * light, green * light, blue * light, alpha)
