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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
    onDarkPagesChange: (Boolean) -> Unit,
    onPageSizeChange: (ZoomMode) -> Unit,
    onPageTurnChange: (PageTurn) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                SwitchRow(
                    label = stringResource(R.string.dark_pages),
                    hint = stringResource(R.string.dark_pages_hint),
                    checked = settings.darkPages,
                    onCheckedChange = onDarkPagesChange,
                )
                SectionTitle(stringResource(R.string.settings_page_size))
                Column(Modifier.selectableGroup()) {
                    RadioRow(
                        label = stringResource(R.string.zoom_fit_page),
                        hint = stringResource(R.string.zoom_fit_page_hint),
                        selected = settings.pageSize == ZoomMode.FIT_PAGE,
                        onClick = { onPageSizeChange(ZoomMode.FIT_PAGE) },
                    )
                    RadioRow(
                        label = stringResource(R.string.zoom_crop),
                        hint = stringResource(R.string.zoom_crop_hint),
                        selected = settings.pageSize == ZoomMode.CROP_MARGINS,
                        onClick = { onPageSizeChange(ZoomMode.CROP_MARGINS) },
                    )
                }
                SectionTitle(stringResource(R.string.settings_page_turn))
                Column(Modifier.selectableGroup()) {
                    RadioRow(
                        label = stringResource(R.string.page_turn_curl),
                        selected = settings.pageTurn == PageTurn.CURL,
                        onClick = { onPageTurnChange(PageTurn.CURL) },
                    )
                    RadioRow(
                        label = stringResource(R.string.page_turn_slide),
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
private fun SwitchRow(label: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
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
