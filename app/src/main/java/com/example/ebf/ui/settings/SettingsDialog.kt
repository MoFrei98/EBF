package com.example.ebf.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ebf.R
import com.example.ebf.data.AppSettings
import com.example.ebf.data.PageTurn
import com.example.ebf.data.ThemeMode
import com.example.ebf.data.ZoomMode

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onThemeChange: (ThemeMode) -> Unit,
    onPageSizeChange: (ZoomMode) -> Unit,
    onPageTurnChange: (PageTurn) -> Unit,
    onChooseLibraryFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.settings_library))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            settings.libraryLabel ?: stringResource(R.string.library_folder_none),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.library_folder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onChooseLibraryFolder) {
                        Text(stringResource(if (settings.libraryTree != null) R.string.change else R.string.choose))
                    }
                }
                SectionTitle(stringResource(R.string.settings_theme))
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                }
                            ),
                            selected = settings.theme == mode,
                            onClick = { onThemeChange(mode) },
                        )
                    }
                }
                SectionTitle(stringResource(R.string.settings_page_size))
                Column(Modifier.selectableGroup()) {
                    listOf(
                        Triple(ZoomMode.WIDE, R.string.zoom_wide, R.string.zoom_wide_hint),
                        Triple(ZoomMode.NORMAL, R.string.zoom_normal, R.string.zoom_normal_hint),
                        Triple(ZoomMode.CLOSE, R.string.zoom_close, R.string.zoom_close_hint),
                    ).forEach { (mode, label, hint) ->
                        RadioRow(
                            label = stringResource(label),
                            hint = stringResource(hint),
                            selected = settings.pageSize == mode,
                            onClick = { onPageSizeChange(mode) },
                        )
                    }
                }
                SectionTitle(stringResource(R.string.settings_page_turn))
                Column(Modifier.selectableGroup()) {
                    RadioRow(
                        label = stringResource(R.string.page_turn_curl),
                        hint = stringResource(R.string.page_turn_curl_hint),
                        selected = settings.pageTurn == PageTurn.CURL,
                        onClick = { onPageTurnChange(PageTurn.CURL) },
                    )
                    RadioRow(
                        label = stringResource(R.string.page_turn_slide),
                        hint = stringResource(R.string.page_turn_slide_hint),
                        selected = settings.pageTurn == PageTurn.SLIDE,
                        onClick = { onPageTurnChange(PageTurn.SLIDE) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, hint: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
