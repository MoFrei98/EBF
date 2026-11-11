package com.example.ebf.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ebf.R
import com.example.ebf.data.Book
import com.example.ebf.data.FolderPaths

/** Eingabe eines Namens, z. B. für einen neuen Ordner. */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Titel, Autor und Erscheinungsjahr eines Buchs bearbeiten. */
@Composable
internal fun BookDetailsDialog(
    book: Book,
    onSave: (title: String, firstName: String, lastName: String, year: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(book.uuid) { mutableStateOf(book.title) }
    var firstName by rememberSaveable(book.uuid) { mutableStateOf(book.authorFirstName.orEmpty()) }
    var lastName by rememberSaveable(book.uuid) { mutableStateOf(book.authorLastName.orEmpty()) }
    var year by rememberSaveable(book.uuid) { mutableStateOf(book.publishYear?.toString().orEmpty()) }
    val yearValid = year.isBlank() || year.trim().toIntOrNull()?.let { it in 1000..2100 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.author_firstname)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.author_lastname)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { input -> year = input.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.publish_year)) },
                    singleLine = true,
                    isError = !yearValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                // Nur zur Info, nicht bearbeitbar.
                if (book.pageCount > 0) {
                    Text(
                        stringResource(R.string.details_pages, book.pageCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.details_location, book.pdfLocation ?: book.fileName ?: "–"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, firstName, lastName, year) },
                enabled = title.isNotBlank() && yearValid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Rückfrage vor einer Aktion, die sich nicht rückgängig machen lässt. */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Erklärt den Bibliotheksordner, bevor der System-Ordnerdialog öffnet. */
@Composable
internal fun LibraryFolderDialog(onChoose: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
        title = { Text(stringResource(R.string.library_folder_title)) },
        text = { Text(stringResource(R.string.library_folder_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onChoose()
            }) { Text(stringResource(R.string.choose_folder)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Zielordner für ein Buch auswählen. */
@Composable
internal fun MoveBookDialog(
    book: Book,
    folders: List<String>,
    onMove: (String) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column {
                Text(
                    book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderRow(
                            label = stringResource(R.string.library_root),
                            depth = 0,
                            current = book.folder == FolderPaths.ROOT,
                            onClick = { onMove(FolderPaths.ROOT) },
                        )
                    }
                    items(folders, key = { it }) { path ->
                        FolderRow(
                            label = FolderPaths.name(path),
                            depth = FolderPaths.depth(path),
                            current = book.folder == path,
                            onClick = { onMove(path) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNewFolder) { Text(stringResource(R.string.new_folder)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FolderRow(label: String, depth: Int, current: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !current, onClick = onClick)
            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        if (current) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.current_folder))
    }
}
