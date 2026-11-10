package com.example.ebf.ui.reader

import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ebf.R
import com.example.ebf.ui.theme.LocalReaderColors

private val PageHPadding = 12.dp
private val PageVPadding = 16.dp

/**
 * Eine einzelne PDF-Seite im Reader.
 *
 * @param onTap Tippen mit x-Position und Breite der Fläche (für die Blätter-Zonen).
 */
@Composable
internal fun PageView(
    vm: ReaderViewModel,
    page: Int,
    onTap: (x: Float, width: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnTap(offset.x, size.width.toFloat()) }
            }
            .windowInsetsPadding(WindowInsets.displayCutout),
        contentAlignment = Alignment.Center,
    ) {
        val pageSize by produceState<Size?>(null, page) { value = vm.pageSize(page) }
        val size = pageSize ?: return@BoxWithConstraints

        val availableWidth = maxWidth - PageHPadding * 2
        val availableHeight = maxHeight - PageVPadding * 2
        val aspect = size.height.toFloat() / size.width
        val width = minOf(availableWidth, availableHeight / aspect)
        val height = width * aspect

        val widthPx = with(LocalDensity.current) { width.roundToPx() }
        val bitmap by produceState<ImageBitmap?>(null, page, widthPx) {
            value = vm.renderPage(page, widthPx)
        }

        Box(
            Modifier
                .size(width, height)
                .shadow(4.dp)
                .background(LocalReaderColors.current.paper)
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.page_description, page + 1),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
