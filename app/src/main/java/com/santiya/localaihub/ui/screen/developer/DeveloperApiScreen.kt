package com.santiya.localaihub.ui.screen.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.runtime.ModelRegistry
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperApiScreen(onNavigateBack: () -> Unit) {
    val registry = ModelRegistry()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SantiyaLocalAiHub API") },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroCard()
            ApiCard("AIDL SDK", "Bind to com.santiya.localaihub.service.LLMService and use SantiyaLocalAiClient for discovery, GGUF streaming, diffusion callbacks, and model manifests.")
            ApiCard("Intent API", "Use PICK_MODEL for catalog selection. RUN_TEXT, RUN_IMAGE, and RUN_VISION are reserved for short delegated flows and currently return a compatibility guard.")
            ApiCard("Local HTTP", "Disabled by default. Planned Ollama-style endpoints are /api/tags, /api/pull, /api/chat, /api/generate, /api/vision/detect, and /api/images/generate.")
            ApiCard("Manifest Schema", registry.manifestSchemaJson())
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF09090B), Color(0xFF2A0F18))
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = "Local AI Runtime For Your Android Apps",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Discover models, import manifests, and connect external apps through AIDL, Intent, or localhost HTTP without sending prompts or images to the cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun ApiCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
