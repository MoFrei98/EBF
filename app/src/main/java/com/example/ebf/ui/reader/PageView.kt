package com.example.ebf.ui.reader

import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ebf.R
import com.example.ebf.data.PageTurn
import com.example.ebf.data.ZoomMode
import com.example.ebf.pdf.Crop
import com.example.ebf.ui.theme.DarkReaderColors
import com.example.ebf.ui.theme.LightReaderColors

private val PageHPadding = 12.dp
private val PageVPadding = 16.dp

/**
 * Eine einzelne PDF-Seite im Reader.
 *
 * Links und rechts wird nie etwas abgeschnitten: Die Seite ist höchstens so breit
 * wie der Bildschirm. Ist sie dadurch höher als der Bildschirm, wird vertikal gescrollt.
 *
 * @param pageOffset Abstand dieser Seite zur aktuell sichtbaren Position im Pager:
 *   0 = sichtbar, > 0 = liegt links (schon umgeblättert), < 0 = liegt rechts (kommt noch).
 * @param darkPages PDF-Seite dunkel darstellen (Dark Mode).
 * @param onTap Tippen mit x-Position und Breite der Fläche (für die Blätter-Zonen).
 */
@Composable
internal fun PageView(
    vm: ReaderViewModel,
    page: Int,
    pageCount: Int,
    zoom: ZoomMode,
    pageTurn: PageTurn,
    darkPages: Boolean,
    pageOffset: () -> Float,
    onTap: (x: Float, width: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val curl = pageTurn == PageTurn.CURL
    val paperColor = if (darkPages) DarkReaderColors.paper else LightReaderColors.paper
    val nightFilter = remember { PageColors.night(DarkReaderColors.paper, DarkReaderColors.ink) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (curl) Modifier.curlTransform(page, pageOffset) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnTap(offset.x, size.width.toFloat()) }
            }
            .windowInsetsPadding(WindowInsets.displayCutout),
        contentAlignment = Alignment.Center,
    ) {
        val pageSize by produceState<Size?>(null, page) { value = vm.pageSize(page) }
        val crop = rememberResult(page, zoom) {
            if (zoom == ZoomMode.CROP_MARGINS) vm.pageCrop(page) else Crop.FULL
        }
        val size = pageSize
        if (size == null || crop == null) return@BoxWithConstraints

        // Seitenverhältnis des sichtbaren Ausschnitts.
        val aspect = (size.height * crop.height) / (size.width * crop.width)
        val availableWidth = maxWidth - PageHPadding * 2
        val availableHeight = maxHeight - PageVPadding * 2
        val width = when (zoom) {
            ZoomMode.FIT_PAGE -> minOf(availableWidth, availableHeight / aspect)
            ZoomMode.FIT_WIDTH, ZoomMode.CROP_MARGINS -> availableWidth
        }
        val height = width * aspect
        val scrollable = height > availableHeight + 1.dp

        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val bitmap = rememberResult(page, widthPx, crop) { vm.renderPage(page, widthPx, crop) }
        // Im Dark Mode erst wissen, ob es eine Bildseite ist, damit nichts aufblitzt.
        val imagePage = if (darkPages) rememberResult(page, "image") { vm.isImagePage(page) } else false
        val colorFilter = when {
            !darkPages -> PageColors.day
            imagePage == true -> PageColors.dimmedImage
            else -> nightFilter
        }

        val paper: @Composable () -> Unit = {
            Paper(
                paperColor = paperColor,
                darkPaper = darkPages,
                readFraction = if (pageCount > 1) page / (pageCount - 1f) else 0f,
                turnShadow = {
                    // Die angehobene Seite wirft einen Schatten auf die Seite darunter.
                    val offset = pageOffset()
                    if (curl && offset > 0f && offset < 1f) (offset * 10f).coerceAtMost(1f) * (1f - offset) else 0f
                },
                dim = {
                    // Die Seite darunter ist dunkler, solange sie noch bedeckt ist.
                    val offset = pageOffset()
                    if (curl && offset < 0f && offset > -1f) -offset else 0f
                },
                modifier = Modifier.size(width, height),
            ) {
                if (bitmap != null && imagePage != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.page_description, page + 1),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (scrollable) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = PageVPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { paper() }
        } else {
            paper()
        }
    }
}

/**
 * Lädt einen Wert im Hintergrund und liefert ihn nur, solange er zu den aktuellen
 * [keys] gehört. So wird beim Wechsel des Zooms kein veraltetes Bild verzerrt angezeigt.
 */
@Composable
private fun <T : Any> rememberResult(vararg keys: Any?, load: suspend () -> T): T? {
    val keyList = keys.toList()
    val result by produceState<Pair<List<Any?>, T>?>(null, *keys) { value = keyList to load() }
    return result?.takeIf { it.first == keyList }?.second
}

/**
 * Umblätter-Effekt: Die obere Seite gleitet zur Seite, die nächste Seite bleibt
 * liegen und wird darunter freigegeben. Seiten mit kleinerer Nummer liegen oben.
 */
private fun Modifier.curlTransform(page: Int, pageOffset: () -> Float): Modifier =
    zIndex(-page.toFloat()).graphicsLayer {
        val offset = pageOffset()
        if (offset < 0f) {
            // Natürliche Position wäre rechts daneben, wir halten die Seite an Ort und Stelle.
            translationX = offset * size.width
            // Nur die direkt folgende Seite liegt sichtbar darunter.
            alpha = if (offset <= -1f) 0f else 1f
        } else {
            translationX = 0f
            alpha = 1f
        }
    }
