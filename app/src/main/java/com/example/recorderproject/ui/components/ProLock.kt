package com.example.recorderproject.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorderproject.billing.ProFeature

/**
 * Marks [content] as a Pro feature for free users: shows a [ProBadge] in the
 * top-end corner and intercepts taps to open the paywall for [feature] instead
 * of running the control's own click. For Pro users (or when [feature] is null)
 * it renders [content] untouched.
 */
@Composable
fun ProLock(
    feature: ProFeature?,
    isPro: Boolean,
    onUpgrade: (ProFeature) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val locked = feature != null && !isPro
    Box(modifier = modifier) {
        content()
        if (locked) {
            // Transparent tap-catcher covering the control → paywall.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onUpgrade(feature!!) },
            )
            ProBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
        }
    }
}
