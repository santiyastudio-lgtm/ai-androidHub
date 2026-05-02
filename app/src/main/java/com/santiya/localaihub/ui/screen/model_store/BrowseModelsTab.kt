package com.santiya.localaihub.ui.screen.model_store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.hub.ModelCatalogPresentation
import com.santiya.localaihub.models.data.HuggingFaceModel
import com.santiya.localaihub.service.ModelDownloadService
import com.santiya.localaihub.ui.icons.TnIcons
import com.santiya.localaihub.viewmodel.ModelStoreViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ModelsTab(
    models: List<HuggingFaceModel>,
    isLoading: Boolean,
    error: String?,
    downloadStates: Map<String, ModelDownloadService.DownloadState>,
    installedModelIds: Set<String>,
    deviceInfo: Map<String, String>,
    viewModel: ModelStoreViewModel,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    val gemmaRecommendation = remember(models, installedModelIds, deviceInfo) {
        pickGemmaRecommendation(models, installedModelIds, deviceInfo)
    }

    when {
        isLoading && models.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }

        error != null && models.isEmpty() -> {
            EmptyStoreState(
                icon = TnIcons.AlertTriangle,
                title = "Не удалось загрузить каталог",
                body = error,
                actionLabel = "Повторить",
                onAction = onRetry,
            )
        }

        models.isEmpty() -> {
            EmptyStoreState(
                icon = TnIcons.SearchOff,
                title = "Модели не найдены",
                body = "Измените запрос, источники или фильтры в панели поиска.",
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Standards.SpacingMd,
                    end = Standards.SpacingMd,
                    top = Standards.SpacingSm,
                    bottom = Standards.SpacingLg,
                ),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                item("store-summary") {
                    StoreSummaryCard(
                        totalModels = models.size,
                        sourceCount = models.map { it.source }.distinct().size,
                        capabilities = models.flatMap { it.capabilities }.distinct().size,
                    )
                }
                gemmaRecommendation?.let { recommendation ->
                    item("gemma-recommendation") {
                        GemmaRecommendationCard(
                            recommendation = recommendation,
                            onDownloadModel = { onDownload(recommendation.model) },
                            onDownloadProjector = recommendation.projector?.let { projector ->
                                { onDownload(projector) }
                            }
                        )
                    }
                }
                items(items = models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isInstalled = model.id in installedModelIds,
                        downloadState = downloadStates[model.id],
                        onDownload = { onDownload(model) },
                        onCancelDownload = { onCancelDownload(model.id) }
                    )
                }
            }
        }
    }
}

private data class GemmaRecommendation(
    val model: HuggingFaceModel,
    val projector: HuggingFaceModel?,
    val modelInstalled: Boolean,
    val projectorInstalled: Boolean,
    val title: String,
    val body: String,
)

private fun pickGemmaRecommendation(
    models: List<HuggingFaceModel>,
    installedModelIds: Set<String>,
    deviceInfo: Map<String, String>
): GemmaRecommendation? {
    val gemmaModels = models.filter {
        it.id.startsWith("gemma4-e2b-") && !it.id.contains("mmproj")
    }
    if (gemmaModels.isEmpty()) return null

    val projector = models.firstOrNull { it.id == "gemma4-e2b-mmproj-f16" }
    val totalRamMb = deviceInfo["totalRamMb"]?.toIntOrNull() ?: 0
    val safeBudgetMb = if (totalRamMb > 0) (totalRamMb * 0.78f).toInt() else 0
    val sortedByDemand = gemmaModels.sortedByDescending { it.ramEstimateMb ?: 0 }
    val recommended = sortedByDemand.firstOrNull { (it.ramEstimateMb ?: Int.MAX_VALUE) <= safeBudgetMb }
        ?: gemmaModels.minByOrNull { it.ramEstimateMb ?: Int.MAX_VALUE }
        ?: return null

    val modelInstalled = recommended.id in installedModelIds
    val projectorInstalled = projector?.id in installedModelIds
    if (modelInstalled && projectorInstalled) return null

    val presentation = ModelCatalogPresentation.presentationFor(recommended)
    val projectorText = when {
        projector == null -> ""
        projectorInstalled -> " Основной mmproj уже загружен."
        else -> " Для картинок и live потом докачайте mmproj."
    }
    val body = if (totalRamMb > 0) {
        "Под ваше устройство (${totalRamMb} MB RAM) безопаснее начать с ${presentation.titleRu}. Оценка по RAM: ${presentation.ramEstimateMb ?: 0} MB.$projectorText"
    } else {
        "Рекомендуем начать с ${presentation.titleRu}. Это наиболее подходящий вариант из серии по размеру и шансу запуска.$projectorText"
    }

    return GemmaRecommendation(
        model = recommended,
        projector = projector,
        modelInstalled = modelInstalled,
        projectorInstalled = projectorInstalled,
        title = "Рекомендуем Gemma 4 E2B Uncensored",
        body = body,
    )
}

@Composable
private fun GemmaRecommendationCard(
    recommendation: GemmaRecommendation,
    onDownloadModel: () -> Unit,
    onDownloadProjector: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = recommendation.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!recommendation.modelInstalled) {
                    Button(onClick = onDownloadModel) {
                        Text("Скачать ${recommendation.model.resolvedFileName ?: recommendation.model.name}")
                    }
                } else {
                    Text(
                        text = "Основная модель уже загружена.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (!recommendation.projectorInstalled && onDownloadProjector != null) {
                    OutlinedButton(onClick = onDownloadProjector) {
                        Text("Скачать mmproj для камеры и картинок")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreSummaryCard(
    totalModels: Int,
    sourceCount: Int,
    capabilities: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            Text(
                text = "Каталог моделей",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$totalModels моделей • $sourceCount источника • $capabilities направлений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStoreState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = Standards.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
