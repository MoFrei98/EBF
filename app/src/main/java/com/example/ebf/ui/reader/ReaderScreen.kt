package com.example.ebf.ui.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ebf.R
import com.example.ebf.data.AppSettings
import com.example.ebf.data.ZoomMode
import com.example.ebf.ui.settings.ThemeToggleButton
import com.example.ebf.ui.theme.LocalDarkTheme
import com.example.ebf.ui.theme.LocalReaderColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    bookUri: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDark: () -> Unit,
) {
    val vm: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(bookUri))
    val state by vm.state.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalReaderColors.current.backdrop)
    ) {
        when (val s = state) {
            ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is ReaderState.Failed -> ReaderError(
                state = s,
                onBack = onBack,
                onRemove = {
                    vm.removeBook()
                    onBack()
                },
                modifier = Modifier.align(Alignment.Center),
            )
            is ReaderState.Ready -> Reader(vm, s, settings, onBack, onOpenSettings, onToggleDark)
        }
    }
}

@Composable
private fun Reader(
    vm: ReaderViewModel,
    ready: ReaderState.Ready,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDark: () -> Unit,
) {
    val pageCount = ready.pageCount
    val pagerState = rememberPagerState(initialPage = ready.initialPage) { pageCount }
    val scope = rememberCoroutineScope()
    val zoom by vm.zoom.collectAsStateWithLifecycle()
    var barsVisible by rememberSaveable { mutableStateOf(true) }

    // Fortschritt speichern und die Leisten beim Weiterblättern ausblenden.
    LaunchedEffect(pagerState) {
        var firstPage = true
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.onPageSettled(page)
            if (!firstPage) barsVisible = false
            firstPage = false
        }
    }

    ImmersiveMode(enabled = !barsVisible)

    fun goTo(page: Int) {
        if (page in 0 until pageCount) scope.launch { pagerState.animateScrollToPage(page) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            PageView(
                vm = vm,
                page = page,
                pageCount = pageCount,
                zoom = zoom,
                pageTurn = settings.pageTurn,
                darkPages = LocalDarkTheme.current && settings.darkPages,
                pageOffset = { (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction },
                onTap = { x, width ->
                    when {
                        x < width * 0.25f -> goTo(pagerState.currentPage - 1)
                        x > width * 0.75f -> goTo(pagerState.currentPage + 1)
                        else -> barsVisible = !barsVisible
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(title = ready.title, onBack = onBack) {
                ThemeToggleButton(onToggle = onToggleDark)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }

        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = pageCount,
                zoom = zoom,
                onZoomChange = vm::setZoom,
                onSeek = { page -> scope.launch { pagerState.scrollToPage(page) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    zoom: ZoomMode,
    onZoomChange: (ZoomMode) -> Unit,
    onSeek: (Int) -> Unit,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shownPage = dragValue?.roundToInt() ?: currentPage
    Surface(
        color = MaterialTheme.colorScheme.surface,
        // Textfarbe ausdrücklich setzen, sonst erbt sie eine dunkle Farbe von außen.
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZoomMenu(zoom = zoom, onZoomChange = onZoomChange)
                Text(
                    text = stringResource(R.string.page_of, shownPage + 1, pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            if (pageCount > 1) {
                Slider(
                    value = dragValue ?: currentPage.toFloat(),
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.roundToInt()) }
                        dragValue = null
                    },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                )
            }
        }
    }
}

/** Dropdown mit den festen Zoom-Formaten. */
@Composable
private fun ZoomMenu(zoom: ZoomMode, onZoomChange: (ZoomMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(zoom.label()))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.zoom))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ZoomMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(mode.label()), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(mode.hint()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = {
                        if (mode == zoom) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onZoomChange(mode)
                    },
                )
            }
        }
    }
}

private fun ZoomMode.label() = when (this) {
    ZoomMode.FIT_PAGE -> R.string.zoom_fit_page
    ZoomMode.FIT_WIDTH -> R.string.zoom_fit_width
    ZoomMode.CROP_MARGINS -> R.string.zoom_crop
}

private fun ZoomMode.hint() = when (this) {
    ZoomMode.FIT_PAGE -> R.string.zoom_fit_page_hint
    ZoomMode.FIT_WIDTH -> R.string.zoom_fit_width_hint
    ZoomMode.CROP_MARGINS -> R.string.zoom_crop_hint
}

@Composable
private fun ReaderError(
    state: ReaderState.Failed,
    onBack: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            if (state.canRemove) {
                Button(onClick = onRemove) { Text(stringResource(R.string.remove_from_library)) }
            }
        }
    }
}

/** Blendet Status- und Navigationsleiste aus, solange [enabled] gilt. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(enabled) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
