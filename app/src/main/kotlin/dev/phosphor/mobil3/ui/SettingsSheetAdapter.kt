package dev.phosphor.mobil3.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Lives beside the screen's sheet switch, so Settings reopens at the scroll it was left at. */
internal class SettingsPresentationState(val scroll: ScrollState)

@Composable
internal fun rememberSettingsPresentationState(): SettingsPresentationState {
    val scroll = rememberScrollState()
    return remember(scroll) { SettingsPresentationState(scroll) }
}
