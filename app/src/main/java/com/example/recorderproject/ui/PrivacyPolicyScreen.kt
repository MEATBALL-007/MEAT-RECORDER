package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            PolicySection("Last updated: June 2026")

            PolicySection("Overview",
                "MEAT REC is a professional audio recording application. " +
                "This policy explains what data we collect, how we use it, and your rights."
            )
            PolicySection("Microphone Access",
                "MEAT REC requires microphone permission to record audio. " +
                "Audio recordings are stored only on your device or in the cloud backup location " +
                "you choose. We never transmit your recordings to our servers."
            )
            PolicySection("Location Data",
                "When you record, MEAT REC may attach your GPS coordinates to the file's " +
                "metadata (iXML Location tag) so you can remember where a take was recorded. " +
                "Location data is stored only inside the WAV file on your device. " +
                "We do not collect or transmit location data to any server. " +
                "You can disable location tagging in Settings."
            )
            PolicySection("Cloud Backup (Google Drive)",
                "If you enable Google Drive backup, your recordings are uploaded to your " +
                "personal Google Drive account. MEAT REC only accesses files it creates " +
                "(drive.file scope). We cannot access any other files in your Drive. " +
                "Google's Privacy Policy governs data stored in Google Drive."
            )
            PolicySection("Local Network Server",
                "MEAT REC includes an optional local network server for transferring recordings " +
                "to a computer on the same Wi-Fi network. This server is only active while " +
                "the app is open and accepts connections only from your local network. " +
                "No data is sent to the internet via this feature."
            )
            PolicySection("Bluetooth",
                "MEAT REC uses Bluetooth to detect and route audio from Bluetooth microphones " +
                "and headsets. We do not read device names, pair codes, or any Bluetooth " +
                "metadata beyond audio routing."
            )
            PolicySection("Data Storage",
                "All recordings and settings are stored locally on your device. " +
                "No personal data is collected by us. No analytics, no crash reporting, " +
                "no advertising SDKs are included in MEAT REC."
            )
            PolicySection("Contact",
                "Questions? Contact: support@meatrec.app"
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RecorderCharcoalCard, shape = RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = RecorderYellow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        body?.let { Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp) }
    }
}
