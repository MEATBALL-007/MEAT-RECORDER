package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.MeatYellow

/**
 * The "★ PRO" pill — single shared definition for the top bar and any pro-gated
 * control. Visible to free users only; callers decide when to show it.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        text = "★ PRO",
        color = Color(0xFF0C0C10),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MeatYellow)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun ProBadgePreview() {
    ProBadge()
}
