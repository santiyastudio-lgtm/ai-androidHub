package com.santiya.localaihub.ui.screen.home

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.LLMModelViewModel

private data class HomeQuickAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeLandingState(
    modifier: Modifier = Modifier,
    llmModelViewModel: LLMModelViewModel,
    onStoreClick: () -> Unit,
    onFilesClick: () -> Unit,
    onLiveClick: () -> Unit,
    onShowModelPicker: () -> Unit,
) {
    val context = LocalContext.current
    val installedModels = llmModelViewModel.installedModels.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val currentModelId = llmModelViewModel.currentModelID.collectAsStateWithLifecycle().value
    val currentModelName = installedModels.firstOrNull { model -> model.id == currentModelId }?.modelName
        ?: "Модель не выбрана"
    val memoryInfo = remember { deviceMemorySummary(context) }

    val quickActions = remember(onStoreClick, onFilesClick, onLiveClick, onShowModelPicker) {
        listOf(
            HomeQuickAction("Live Beta", "Камера, лица и VLM", TnIcons.Eye, onLiveClick),
            HomeQuickAction("Модели", "Выбор и загрузка", TnIcons.Stack2, onShowModelPicker),
            HomeQuickAction("Магазин", "HF, Civitai, ModelScope, GitHub", TnIcons.Download, onStoreClick),
            HomeQuickAction("Файлы", "Workspace и документы", TnIcons.FileText, onFilesClick),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SantiyaLocalAiHub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Локальный AI hub для моделей, live-камеры и интеграции с другими приложениями.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    onClick = onShowModelPicker,
                    shape = RoundedCornerShape(999.dp),
                    color = if (currentModelId.isNullOrBlank()) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (currentModelId.isNullOrBlank()) TnIcons.AlertTriangle else TnIcons.CircleCheck,
                            contentDescription = null
                        )
                        Column {
                            Text(
                                text = "Текущая модель",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentModelName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Установлено", installedModels.size.toString())
                MetricColumn("RAM", memoryInfo)
                MetricColumn("LAN", "Локально")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickActions) { action ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = action.onClick),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun deviceMemorySummary(context: Context): String {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(memoryInfo)
    return "${(memoryInfo.totalMem / (1024L * 1024L * 1024L)).coerceAtLeast(1)} GB"
}
