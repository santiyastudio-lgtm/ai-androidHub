package com.santiya.localaihub.ui.screen.home

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.LLMModelViewModel

private data class HomeQuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeLandingState(
    modifier: Modifier = Modifier,
    llmModelViewModel: LLMModelViewModel,
    onStoreClick: () -> Unit,
    onFilesClick: () -> Unit,
    onLiveClick: () -> Unit,
    onOfflineCityClick: () -> Unit,
    onApiModelsClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onShowModelPicker: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val installedModels = llmModelViewModel.installedModels
        .collectAsStateWithLifecycle(initialValue = emptyList()).value
    val currentModelName = llmModelViewModel.currentModelName.collectAsStateWithLifecycle().value
        ?: localizedText("Модель не выбрана", "No model selected")

    val quickActions = listOf(
        HomeQuickAction(
            title = localizedText("OpenClaw локально", "OpenClaw Local"),
            subtitle = localizedText(
                "Локальный агент на GGUF-моделях внутри приложения. Без мессенджеров и внешних чатов.",
                "Local agent running GGUF models inside the app. No messengers and no external chats."
            ),
            icon = TnIcons.MessageCircle,
            onClick = onShowModelPicker,
        ),
        HomeQuickAction(
            title = localizedText("API-модели", "API Models"),
            subtitle = localizedText(
                "OpenAI, OpenRouter, DeepSeek, Claude и Gemini через ваш API ключ",
                "OpenAI, OpenRouter, DeepSeek, Claude, and Gemini with your API key"
            ),
            icon = TnIcons.World,
            onClick = onApiModelsClick,
        ),
        HomeQuickAction(
            title = localizedText("Live AI", "Live AI"),
            subtitle = localizedText(
                "Камера, анализ сцены и live-ассистент",
                "Camera, scene analysis, and live assistant"
            ),
            icon = TnIcons.Eye,
            onClick = onLiveClick,
        ),
        HomeQuickAction(
            title = localizedText("Модели", "Models"),
            subtitle = localizedText(
                "Выбор и загрузка локальных моделей",
                "Pick and install local models"
            ),
            icon = TnIcons.Stack2,
            onClick = onShowModelPicker,
        ),
        HomeQuickAction(
            title = localizedText("Магазин", "Store"),
            subtitle = localizedText(
                "Hugging Face, GitHub и внешние источники",
                "Hugging Face, GitHub, and external sources"
            ),
            icon = TnIcons.Download,
            onClick = onStoreClick,
        ),
        HomeQuickAction(
            title = localizedText("Файлы", "Files"),
            subtitle = localizedText(
                "Workspace, документы и вложения",
                "Workspace, documents, and attachments"
            ),
            icon = TnIcons.FileText,
            onClick = onFilesClick,
        ),
        HomeQuickAction(
            title = localizedText("Офлайн-город", "Offline city"),
            subtitle = localizedText(
                "Локальная карта, места, остановки и расписание автобусов",
                "Local map, places, stops, and bus schedules"
            ),
            icon = TnIcons.World,
            onClick = onOfflineCityClick,
        ),
        HomeQuickAction(
            title = localizedText("Браузер", "Browser"),
            subtitle = localizedText(
                "Встроенный браузер для OpenClaw, веб-поиска и страниц из ответов агента",
                "Embedded browser for OpenClaw, web search, and pages from agent answers"
            ),
            icon = TnIcons.World,
            onClick = onBrowserClick,
        ),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 180.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "SantiyaLocalAiHub",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = localizedText(
                            "OpenClaw локально по умолчанию: локальный чат на вашей модели, лайв-камера, файлы и офлайн-город внутри одного приложения.",
                            "OpenClaw Local by default: local chat on your model, live camera, files, and offline city inside one app."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        modifier = Modifier.clickable(onClick = onShowModelPicker),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                text = localizedText("Текущая модель", "Current model"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = currentModelName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricColumn(localizedText("Установлено", "Installed"), installedModels.size.toString())
                    MetricColumn("RAM", deviceMemorySummary(context))
                    MetricColumn("LAN", localizedText("Локально", "Local"))
                }
            }
        }

        items(quickActions) { action ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = action.onClick),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun deviceMemorySummary(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memoryInfo)
    return "${(memoryInfo.totalMem / (1024L * 1024L * 1024L)).coerceAtLeast(1)} GB"
}
