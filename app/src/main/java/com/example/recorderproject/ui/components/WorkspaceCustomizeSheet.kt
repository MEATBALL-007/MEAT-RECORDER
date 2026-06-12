package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.moveDown
import com.example.recorderproject.model.moveUp
import com.example.recorderproject.model.toggleVisible
import com.example.recorderproject.ui.theme.MeatYellow

private val CardBg = Color(0xFF161616)
private val Charcoal = Color(0xFF101014)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceCustomizeSheet(
    layout: WorkspaceLayout,
    isPro: Boolean,
    modeName: String,
    onSave: (WorkspaceLayout) -> Unit,
    onReset: () -> Unit,
    onUpgrade: (ProFeature) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var working by remember { mutableStateOf(layout) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Charcoal, dragHandle = null) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Customize layout", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                if (isPro) "Saved to $modeName mode" else "Shared across all modes",
                color = MeatYellow, fontSize = 12.sp,
            )

            working.items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(item.control.label, color = if (item.visible) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (item.control.proFeature != null && !isPro) ProBadge()
                    Text("", modifier = Modifier.weight(1f))
                    Text(if (item.visible) "👁" else "🚫", fontSize = 16.sp, modifier = Modifier.clickable { working = working.toggleVisible(index) })
                    Text("▲", color = if (index == 0) Color.White.copy(alpha = 0.25f) else MeatYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { working = working.moveUp(index) })
                    Text("▼", color = if (index == working.items.lastIndex) Color.White.copy(alpha = 0.25f) else MeatYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { working = working.moveDown(index) })
                }
            }

            if (!isPro) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg).clickable { onUpgrade(ProFeature.CUSTOM_WORKSPACE) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProBadge()
                    Text("Save a separate layout per mode", color = Color.White, fontSize = 13.sp)
                }
            }

            Button(
                onClick = { onSave(working); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA4616)),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Done", color = Color.White, fontWeight = FontWeight.Bold) }

            TextButton(
                onClick = { working = WorkspaceLayout.DEFAULT; onReset(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset to default", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
        }
    }
}
