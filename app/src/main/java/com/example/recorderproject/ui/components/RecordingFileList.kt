package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun RecordingFileList(
    files: List<RecordFile>,
    onTapFile: (RecordFile) -> Unit,
    onTapEQ: (RecordFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("No recordings yet — tap Record to start", color = RecorderBlueGrey)
        }
        return
    }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(files) { file -> FileRow(file, onTap = { onTapFile(file) }, onTapEQ = { onTapEQ(file) }) }
    }
}

@Composable
private fun FileRow(file: RecordFile, onTap: () -> Unit, onTapEQ: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RecorderCharcoalCard)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${file.durationSeconds}s",
                    color = RecorderBlueGrey, fontSize = 11.sp,
                )
                if (file.hasNoiseReduction) Badge("NR")
                if (file.hasEQ) Badge("EQ")
                if (file.isLocked) Badge("🔒")
            }
        }
        Text(
            "EQ →",
            color = RecorderOrange,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C0C10))
                .clickable(onClick = onTapEQ)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = RecorderYellow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1F1F23))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
