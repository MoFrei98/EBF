package com.example.ebf.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ebf.R
import com.example.ebf.data.Book
import com.example.ebf.data.FolderPaths
import com.example.ebf.data.LibrarySort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Offene Dialoge der Bibliothek. */
private sealed interface LibraryDialog {
    data object ChooseLibraryFolder : LibraryDialog
    data class CreateFolder(val moveInto: Book? = null) : LibraryDialog
    data class RenameFolder(val path: String) : LibraryDialog
    data class DeleteFolder(val path: String) : LibraryDialog
    data class MoveBook(val book: Book) : LibraryDialog
    data class RenameBook(val book: Book) : LibraryDialog
    data class RemoveBook(val book: Book) : LibraryDialog
    data class DeleteBook(val book: Book) : LibraryDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenBook: (String) -> Unit,
    onChooseLibraryFolder: () -> Unit,
    topBarActions: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val pickPdf: () -> Unit = { picker.launch(arrayOf("application/pdf")) }

    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val ui = state
    val inFolder = ui != null && ui.path.isNotEmpty()
    BackHandler(enabled = inFolder) { vm.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (inFolder) FolderPaths.name(ui!!.path) else stringResource(R.string.library_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (inFolder && FolderPaths.depth(ui!!.path) > 1) {
                            Text(
                                FolderPaths.parent(ui.path).replace("/", " › "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (inFolder) {
                        IconButton(onClick = { vm.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    topBarActions()
                    LibraryMenu(
                        sort = ui?.sort ?: LibrarySort.RECENT,
                        hasLibraryFolder = ui?.hasLibraryFolder == true,
                        onNewFolder = {
                            dialog = if (ui?.hasLibraryFolder == true) LibraryDialog.CreateFolder()
                            else LibraryDialog.ChooseLibraryFolder
                        },
                        onSort = vm::setSort,
                        onRefresh = vm::refresh,
                    )
                },
            )
        },
        floatingActionButton = {
            if (ui != null && (ui.books.isNotEmpty() || ui.folders.isNotEmpty() || inFolder)) {
                ExtendedFloatingActionButton(
                    onClick = pickPdf,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_pdf)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                ui == null -> Unit
                ui.books.isEmpty() && ui.folders.isEmpty() -> EmptyState(
                    inFolder = inFolder,
                    hasLibraryFolder = ui.hasLibraryFolder,
                    onAdd = pickPdf,
                    onChooseLibraryFolder = { dialog = LibraryDialog.ChooseLibraryFolder },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.folders, key = { "folder:" + it.path }) { tile ->
                        FolderCard(
                            tile = tile,
                            coverFile = vm::coverFile,
                            onOpen = { vm.openFolder(tile.path) },
                            onRename = { dialog = LibraryDialog.RenameFolder(tile.path) },
                            onDelete = { dialog = LibraryDialog.DeleteFolder(tile.path) },
                        )
                    }
                    items(ui.books, key = { "book:" + it.uri }) { book ->
                        BookCard(
                            book = book,
                            coverFile = vm.coverFile(book),
                            onClick = { onOpenBook(book.uri) },
                            onMove = {
                                dialog = if (ui.hasLibraryFolder) LibraryDialog.MoveBook(book)
                                else LibraryDialog.ChooseLibraryFolder
                            },
                            onRename = { dialog = LibraryDialog.RenameBook(book) },
                            onRemove = {
                                dialog = if (book.inLibraryFolder) LibraryDialog.DeleteBook(book)
                                else LibraryDialog.RemoveBook(book)
                            },
                        )
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        LibraryDialog.ChooseLibraryFolder -> LibraryFolderDialog(onChoose = onChooseLibraryFolder, onDismiss = close)
        is LibraryDialog.CreateFolder -> NameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                vm.createFolder(name, moveInto = d.moveInto)
                close()
            },
            onDismiss = close,
        )
        is LibraryDialog.RenameFolder -> NameDialog(
            title = stringResource(R.string.rename_folder),
            initial = FolderPaths.name(d.path),
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                vm.renameFolder(d.path, name)
                close()
            },
            onDismiss = close,
        )
        is LibraryDialog.DeleteFolder -> ConfirmDialog(
            title = stringResource(R.string.delete_folder_title),
            text = stringResource(R.string.delete_folder_text, FolderPaths.name(d.path)),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteFolder(d.path)
                close()
            },
            onDismiss = close,
        )
        is LibraryDialog.MoveBook -> MoveBookDialog(
            book = d.book,
            folders = ui?.allFolders.orEmpty(),
            onMove = { path ->
                vm.moveBook(d.book, path)
                close()
            },
            onNewFolder = { dialog = LibraryDialog.CreateFolder(moveInto = d.book) },
            onDismiss = close,
        )
        is LibraryDialog.RenameBook -> NameDialog(
            title = stringResource(R.string.rename_title),
            initial = d.book.title,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { title ->
                vm.rename(d.book, title)
                close()
            },
            onDismiss = close,
        )
        is LibraryDialog.RemoveBook -> ConfirmDialog(
            title = stringResource(R.string.remove_title),
            text = stringResource(R.string.remove_text, d.book.title),
            confirmLabel = stringResource(R.string.remove),
            onConfirm = {
                vm.remove(d.book)
                close()
            },
            onDismiss = close,
        )
        is LibraryDialog.DeleteBook -> ConfirmDialog(
            title = stringResource(R.string.delete_book_title),
            text = stringResource(R.string.delete_book_text, d.book.title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteBook(d.book)
                close()
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun LibraryMenu(
    sort: LibrarySort,
    hasLibraryFolder: Boolean,
    onNewFolder: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    onRefresh: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_folder)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                onClick = {
                    open = false
                    onNewFolder()
                },
            )
            if (hasLibraryFolder) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        open = false
                        onRefresh()
                    },
                )
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.sort_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            listOf(LibrarySort.RECENT to R.string.sort_recent, LibrarySort.TITLE to R.string.sort_title)
                .forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            open = false
                            onSort(option)
                        },
                    )
                }
        }
    }
}

// ------------------------------------------------------------------- Ordner

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    tile: FolderTile,
    coverFile: (Book) -> File,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.covers.isEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(72.dp),
                )
            } else {
                FannedCovers(tile.covers, coverFile)
            }
            // Ordner-Abzeichen unten links
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    tile.bookCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = tile.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.folder_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            pluralStringResource(R.plurals.book_count, tile.bookCount, tile.bookCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Bis zu drei Cover, leicht aufgefächert wie Bücher auf einem Stapel. */
@Composable
private fun FannedCovers(covers: List<Book>, coverFile: (Book) -> File) {
    val angles = when (covers.size) {
        1 -> listOf(0f)
        2 -> listOf(-8f, 8f)
        else -> listOf(-11f, 0f, 11f)
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shift = with(LocalDensity.current) { (maxWidth * 0.16f).toPx() }
        covers.forEachIndexed { index, book ->
            val angle = angles[index]
            CoverImage(
                book = book,
                file = coverFile(book),
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .aspectRatio(0.707f)
                    .graphicsLayer {
                        rotationZ = angle
                        translationX = shift * (angle / 11f)
                        translationY = -shift * 0.15f
                    }
                    .shadow(4.dp, RoundedCornerShape(3.dp)),
            )
        }
    }
}

// ------------------------------------------------------------------- Bücher

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    coverFile: File,
    onClick: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(8.dp)
    ) {
        CoverImage(
            book = book,
            file = coverFile,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .shadow(3.dp, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.book_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_folder)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMove()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (book.inLibraryFolder) R.string.delete else R.string.remove))
                        },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (book.pageCount > 0) {
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.page_of, book.lastPage + 1, book.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.book_new),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CoverImage(book: Book, file: File, modifier: Modifier = Modifier) {
    // pageCount ändert sich, sobald ein neu gefundenes Buch sein Cover bekommen hat.
    val cover by produceState<ImageBitmap?>(null, file.path, book.pageCount) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.cover_description, book.title),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_book),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(
    inFolder: Boolean,
    hasLibraryFolder: Boolean,
    onAdd: () -> Unit,
    onChooseLibraryFolder: () -> Unit,
) {
    // Scrollbar, damit "Ziehen zum Aktualisieren" auch hier funktioniert.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(96.dp))
        Icon(
            painterResource(if (inFolder) R.drawable.ic_folder else R.drawable.ic_book),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (inFolder) R.string.empty_folder_title else R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (inFolder) R.string.empty_folder_text else R.string.empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_pdf))
        }
        if (!inFolder && !hasLibraryFolder) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onChooseLibraryFolder) {
                Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_folder_choose))
            }
        }
    }
}
