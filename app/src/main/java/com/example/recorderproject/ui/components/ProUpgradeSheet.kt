package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Paywall bottom sheet. Opened when a free user taps a locked feature; [highlight] is that
 * feature so we can lead with what they were trying to do. Price comes live from Play Billing
 * ([priceText]); shows a neutral fallback before product details load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpgradeSheet(
    highlight: ProFeature?,
    priceText: String?,
    originalPriceText: String? = null,
    onUpgrade: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Live price when Play is connected; otherwise the launch-promo fallbacks so the
    // offer is visible even before the Play Console product is set up.
    val promoPrice = priceText ?: com.example.recorderproject.billing.BillingManager.FALLBACK_PROMO_PRICE
    val wasPrice = originalPriceText ?: com.example.recorderproject.billing.BillingManager.FALLBACK_ORIGINAL_PRICE
    val discount = com.example.recorderproject.billing.BillingManager.LAUNCH_DISCOUNT_PERCENT
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = RecorderCharcoalCard,
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = RecorderYellow, modifier = Modifier.size(26.dp))
                Text("MEAT REC Pro", color = RecorderYellow, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.weight(1f))
                // Launch promo badge
                Text(
                    "เปิดตัว -$discount%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RecorderOrange)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            highlight?.let {
                Text(
                    "Unlock ${it.title} — ${it.blurb}.",
                    color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }

            Text(
                "One payment. Yours forever. No subscription.",
                color = RecorderBlueGrey, fontSize = 13.sp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                ProFeature.entries.forEach { f -> FeatureLine(f.title, f.blurb) }
            }

            // Price line — struck-through "was" price next to the launch price.
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    wasPrice,
                    color = RecorderBlueGrey,
                    fontSize = 16.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
                Text(
                    promoPrice,
                    color = RecorderYellow,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "lifetime",
                    color = RecorderBlueGrey,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "Upgrade now — $promoPrice",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text("Restore purchase", color = RecorderBlueGrey, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, blurb: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0C0C10))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFC72C)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = RecorderYellow, modifier = Modifier.size(14.dp))
        }
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(blurb, color = RecorderBlueGrey, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
