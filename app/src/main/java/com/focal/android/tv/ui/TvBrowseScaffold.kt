package com.focal.android.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TvLayout {
    val screenHorizontalPadding: Dp = 48.dp
    val screenVerticalPadding: Dp = 32.dp
    val sectionSpacing: Dp = 20.dp
    val senderCardMinWidth: Dp = 360.dp
    val compactHeaderBreakpoint: Dp = 860.dp
}

/**
 * Root browse shell for the TV flavor: fixed header, scrollable body with bounded height.
 */
@Composable
fun TvBrowseScaffold(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = TvLayout.screenHorizontalPadding,
                    vertical = TvLayout.screenVerticalPadding
                )
        ) {
            header()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = TvLayout.sectionSpacing),
                content = content
            )
        }
    }
}

@Composable
fun TvResponsiveHeader(
    branding: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < TvLayout.compactHeaderBreakpoint) {
            Column(modifier = Modifier.fillMaxWidth()) {
                branding()
                Spacer(modifier = Modifier.height(16.dp))
                actions()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                branding()
                actions()
            }
        }
    }
}
