package com.example.ebf.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.ebf.R
import com.example.ebf.ui.theme.LocalDarkTheme

/** Schneller Umschalter zwischen hellem und dunklem Design. */
@Composable
fun ThemeToggleButton(onToggle: () -> Unit) {
    val dark = LocalDarkTheme.current
    IconButton(onClick = onToggle) {
        Icon(
            painter = painterResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode),
            contentDescription = stringResource(if (dark) R.string.switch_to_light else R.string.switch_to_dark),
        )
    }
}
