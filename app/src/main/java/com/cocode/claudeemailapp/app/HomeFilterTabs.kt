package com.cocode.claudeemailapp.app

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.cocode.claudeemailapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeFilterTabs(
    selected: AppViewModel.HomeFilter,
    counts: AppViewModel.HomeBuckets,
    onSelect: (AppViewModel.HomeFilter) -> Unit
) {
    // The row fills the screen at normal text. At large text the three equal tabs, each as wide as the
    // longest word, no longer fit, so the row scrolls sideways instead of breaking "Archived" mid-word.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(min = fullWidth).testTag("home_filter_tabs")) {
                FilterButton(0, AppViewModel.HomeFilter.ACTIVE, R.string.home_filter_active, counts.active.size, selected, onSelect)
                FilterButton(1, AppViewModel.HomeFilter.WAITING, R.string.home_filter_waiting, counts.waiting.size, selected, onSelect)
                FilterButton(2, AppViewModel.HomeFilter.ARCHIVED, R.string.home_filter_archived, counts.archived.size, selected, onSelect)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.FilterButton(
    index: Int,
    filter: AppViewModel.HomeFilter,
    @StringRes label: Int,
    count: Int,
    selected: AppViewModel.HomeFilter,
    onSelect: (AppViewModel.HomeFilter) -> Unit
) {
    val text = stringResource(label)
    SegmentedButton(
        selected = selected == filter,
        onClick = { onSelect(filter) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
        modifier = Modifier.testTag("home_filter_${filter.name.lowercase()}")
    ) {
        Text(
            text = if (count > 0) stringResource(R.string.home_filter_with_count, text, count) else text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
